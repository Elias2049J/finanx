package com.elias.mismonedas.service.impl;

import com.elias.mismonedas.dto.payment.ReceiptResponse;
import com.elias.mismonedas.entity.Category;
import com.elias.mismonedas.entity.Image;
import com.elias.mismonedas.entity.Receipt;
import com.elias.mismonedas.entity.User;
import com.elias.mismonedas.entity.enums.SubscriptionPlan;
import com.elias.mismonedas.plan.gating.PlanSubject;
import com.elias.mismonedas.plan.gating.RequiresPlan;
import com.elias.mismonedas.exception.OcrProcessingException;
import com.elias.mismonedas.port.OcrPort;
import com.elias.mismonedas.port.OcrResult;
import com.elias.mismonedas.port.ReceiptData;
import com.elias.mismonedas.port.ReceiptParserPort;
import com.elias.mismonedas.repository.CategoryRepository;
import com.elias.mismonedas.repository.ReceiptRepository;
import com.elias.mismonedas.repository.UserRepository;
import com.elias.mismonedas.service.ImageService;
import com.elias.mismonedas.service.ReceiptScanService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ReceiptScanServiceImpl implements ReceiptScanService {

    private static final double CONFIDENCE_THRESHOLD = 0.55;

    private final OcrPort ocrPort;
    private final ReceiptParserPort regexParser;
    private final ReceiptParserPort geminiParser;
    private final ReceiptRepository receiptRepository;
    private final ImageService imageService;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final Cache<String, CachedScanResult> scanCache;

    record CachedScanResult(OcrResult ocrResult, ReceiptData data, String engine) {}

    public ReceiptScanServiceImpl(OcrPort ocrPort,
                                  @Qualifier("regex") ReceiptParserPort regexParser,
                                  @Qualifier("gemini") ReceiptParserPort geminiParser,
                                  ReceiptRepository receiptRepository,
                                  ImageService imageService,
                                  UserRepository userRepository,
                                  CategoryRepository categoryRepository,
                                  @Value("${app.ocr.cache-max-size:50}") int cacheMaxSize,
                                  @Value("${app.ocr.cache-ttl-minutes:60}") int cacheTtlMinutes) {
        this.ocrPort = ocrPort;
        this.regexParser = regexParser;
        this.geminiParser = geminiParser;
        this.receiptRepository = receiptRepository;
        this.imageService = imageService;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.scanCache = Caffeine.newBuilder()
                .maximumSize(cacheMaxSize)
                .expireAfterWrite(cacheTtlMinutes, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    @Override
    @RequiresPlan(SubscriptionPlan.PREMIUM)
    @Transactional
    public ReceiptResponse scanReceipt(@PlanSubject Long userId, byte[] imageBytes, String filename, String mediaType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + userId));

        String imageHash = sha256(imageBytes);
        CachedScanResult cached = scanCache.getIfPresent(imageHash);
        OcrResult ocrResult;
        ReceiptData data;
        String engineUsed;

        if (cached != null) {
            log.info("ocr_cache_hit hash={}", imageHash);
            ocrResult = cached.ocrResult();
            data = cached.data();
            engineUsed = cached.engine();
        } else {
            ocrResult = ocrPort.extractText(imageBytes);
            log.info("ocr_raw_text_preview={}", truncate(ocrResult.rawText(), 500));
            log.info("ocr_duration_ms={} confidence={}", ocrResult.durationMs(), ocrResult.engineConfidence());

            data = regexParser.parse(ocrResult.rawText());
            log.info("regex_parse_result amount={} date={} description={} paymentMethod={} transactionType={} currency={} confidence={}",
                    data.amount(), data.date(), data.description(), data.paymentMethod(), data.transactionType(), data.currencyGuess(), data.parseConfidence());
            engineUsed = "REGEX";
            if (data.parseConfidence() < CONFIDENCE_THRESHOLD) {
                log.warn("regex_confidence={} below_threshold={}, escalating_to_Gemini", data.parseConfidence(), CONFIDENCE_THRESHOLD);
                try {
                    data = geminiParser.parse(ocrResult.rawText());
                    engineUsed = "GEMINI";
                    log.info("gemini_parse_result amount={} date={} description={} paymentMethod={} transactionType={} currency={} confidence={}",
                            data.amount(), data.date(), data.description(), data.paymentMethod(), data.transactionType(), data.currencyGuess(), data.parseConfidence());
                } catch (Exception e) {
                    log.warn("gemini_fallback_failed, using regex result", e);
                }
            }

            log.info("final_parsed_data engine={} amount={} date={} description={} paymentMethod={} transactionType={} confidence={}",
                    engineUsed, data.amount(), data.date(), data.description(), data.paymentMethod(), data.transactionType(), data.parseConfidence());

            scanCache.put(imageHash, new CachedScanResult(ocrResult, data, engineUsed));
        }

        if (data.amount() == null) {
            throw new OcrProcessingException(
                    "No se pudo extraer información válida del recibo. " +
                    "No se detectó el monto. Verifica que la imagen sea legible e inténtalo de nuevo."
            );
        }

        Long detectedCategoryId = matchCategory(userId, data, ocrResult.rawText());

        Receipt receipt = Receipt.builder()
                .user(user)
                .rawOcrText(ocrResult.rawText())
                .detectedAmount(data.amount())
                .detectedDate(data.date())
                .description(data.description())
                .paymentMethod(data.paymentMethod())
                .transactionType(data.transactionType())
                .detectedCategoryId(detectedCategoryId)
                .currencyGuess(data.currencyGuess())
                .confidenceScore(data.parseConfidence())
                .parserEngineUsed(engineUsed)
                .ocrDurationMs(ocrResult.durationMs())
                .build();

        receipt = receiptRepository.save(receipt);

        if (filename != null && mediaType != null && imageBytes != null) {
            Image image = imageService.save(user, filename, mediaType, imageBytes);
            receipt.setImage(image);
            receiptRepository.save(receipt);
            log.info("receipt_image_saved receipt_id={} image_id={} filename={} size={}bytes",
                    receipt.getId(), image.getId(), image.getFilename(), imageBytes.length);
        }

        log.info("scan_complete receipt_id={} userId={} engine={} amount={} currency={} duration_ms={}",
                receipt.getId(), userId, engineUsed, data.amount(), data.currencyGuess(), ocrResult.durationMs());

        String rawText = ocrResult.rawText();
        Long imageId = receipt.getImage() != null ? receipt.getImage().getId() : null;
        return new ReceiptResponse(
                receipt.getId(),
                imageId,
                data.amount(),
                data.date(),
                data.description(),
                data.paymentMethod(),
                data.transactionType(),
                detectedCategoryId,
                rawText,
                data.currencyGuess(),
                data.parseConfidence(),
                engineUsed
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptResponse getScanResult(Long userId, Long receiptId) {
        Receipt receipt = receiptRepository.findByIdAndUser_Id(receiptId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Recibo no encontrado: " + receiptId));

        Long imageId = receipt.getImage() != null ? receipt.getImage().getId() : null;
        return new ReceiptResponse(
                receipt.getId(),
                imageId,
                receipt.getDetectedAmount(),
                receipt.getDetectedDate(),
                receipt.getDescription(),
                receipt.getPaymentMethod(),
                receipt.getTransactionType(),
                receipt.getDetectedCategoryId(),
                receipt.getRawOcrText(),
                receipt.getCurrencyGuess(),
                receipt.getConfidenceScore(),
                receipt.getParserEngineUsed()
        );
    }

    @Override
    @Transactional
    public ReceiptResponse improveWithAi(Long receiptId, Long userId) {
        Receipt receipt = receiptRepository.findByIdAndUser_Id(receiptId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Recibo no encontrado: " + receiptId));

        String rawText = receipt.getRawOcrText();
        if (rawText == null || rawText.isBlank()) {
            throw new OcrProcessingException("El recibo no tiene texto OCR para mejorar");
        }

        double oldConfidence = receipt.getConfidenceScore();
        ReceiptData improved = geminiParser.parse(rawText);
        if (improved.parseConfidence() > oldConfidence) {
            Long detectedCategoryId = matchCategory(userId, improved, rawText);
            receipt.setDetectedAmount(improved.amount());
            receipt.setDetectedDate(improved.date());
            receipt.setDescription(improved.description());
            receipt.setPaymentMethod(improved.paymentMethod());
            receipt.setTransactionType(improved.transactionType());
            receipt.setDetectedCategoryId(detectedCategoryId);
            receipt.setCurrencyGuess(improved.currencyGuess());
            receipt.setConfidenceScore(improved.parseConfidence());
            receipt.setParserEngineUsed("GEMINI");
            receipt = receiptRepository.save(receipt);
            log.info("improve_ai accepted receipt_id={} new_confidence={} old_confidence={}",
                    receiptId, improved.parseConfidence(), oldConfidence);
        } else {
            log.info("improve_ai skipped receipt_id={} ai_confidence={} did_not_exceed={}",
                    receiptId, improved.parseConfidence(), oldConfidence);
        }

        Long imageId = receipt.getImage() != null ? receipt.getImage().getId() : null;
        return new ReceiptResponse(
                receipt.getId(),
                imageId,
                receipt.getDetectedAmount(),
                receipt.getDetectedDate(),
                receipt.getDescription(),
                receipt.getPaymentMethod(),
                receipt.getTransactionType(),
                receipt.getDetectedCategoryId(),
                receipt.getRawOcrText(),
                receipt.getCurrencyGuess(),
                receipt.getConfidenceScore(),
                receipt.getParserEngineUsed()
        );
    }

    private Long matchCategory(Long userId, ReceiptData data, String rawOcrText) {
        if (data.category() != null && !data.category().isBlank()) {
            List<Category> matches = categoryRepository.findByUserIdAndKeyword(userId, data.category().trim());
            if (!matches.isEmpty()) {
                Long categoryId = matches.getFirst().getId();
                log.info("category_matched_from_ai userId={} category={} categoryId={}", userId, data.category(), categoryId);
                return categoryId;
            }
            log.debug("category_from_ai_not_found_in_db userId={} category={}", userId, data.category());
        }

        String searchText = rawOcrText != null ? rawOcrText : "";
        if (data.description() != null && !data.description().isBlank()) {
            searchText = data.description() + " " + searchText;
        }

        String[] keywords = searchText.trim().split("\\s+");
        for (String keyword : keywords) {
            if (keyword.length() < 3) continue;

            List<Category> matches = categoryRepository.findByUserIdAndKeyword(userId, keyword);
            if (!matches.isEmpty()) {
                Long categoryId = matches.getFirst().getId();
                log.info("category_matched_from_text userId={} keyword={} categoryId={}", userId, keyword, categoryId);
                return categoryId;
            }
        }

        log.debug("no_category_match userId={} description={}", userId, data.description());
        return null;
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "null";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
