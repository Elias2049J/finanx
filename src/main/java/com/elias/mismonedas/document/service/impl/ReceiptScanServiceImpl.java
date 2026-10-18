package com.elias.mismonedas.document.service.impl;

import com.elias.mismonedas.billing.domain.enums.SubscriptionPlan;
import com.elias.mismonedas.billing.plan.RequiresPlan;
import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.document.domain.entity.Image;
import com.elias.mismonedas.document.domain.entity.Receipt;
import com.elias.mismonedas.document.domain.enums.ReceiptScanStatus;
import com.elias.mismonedas.document.dto.ReceiptResponse;
import com.elias.mismonedas.document.dto.scan.ReceiptScanResultMessage;
import com.elias.mismonedas.document.outbox.ReceiptScanJobDispatcher;
import com.elias.mismonedas.document.outbox.ReceiptScanOutbox;
import com.elias.mismonedas.document.outbox.ReceiptScanOutboxState;
import com.elias.mismonedas.document.outbox.persistence.ReceiptScanOutboxRepository;
import com.elias.mismonedas.document.port.ReceiptData;
import com.elias.mismonedas.document.port.ReceiptParserPort;
import com.elias.mismonedas.document.repository.ReceiptRepository;
import com.elias.mismonedas.document.service.ImageService;
import com.elias.mismonedas.document.service.ReceiptScanService;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.shared.exception.OcrProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
public class ReceiptScanServiceImpl implements ReceiptScanService {

    private static final double CONFIDENCE_THRESHOLD = 0.55;

    private final ReceiptParserPort regexParser;
    private final ReceiptParserPort geminiParser;
    private final ReceiptRepository receiptRepository;
    private final ImageService imageService;
    private final SecurityService securityService;
    private final CategoryRepository categoryRepository;
    private final ReceiptScanOutboxRepository outboxRepository;
    private final ReceiptScanJobDispatcher jobDispatcher;

    public ReceiptScanServiceImpl(@Qualifier("regex") ReceiptParserPort regexParser,
                                  @Qualifier("gemini") ReceiptParserPort geminiParser,
                                  ReceiptRepository receiptRepository,
                                  ImageService imageService,
                                  SecurityService securityService,
                                  CategoryRepository categoryRepository,
                                  ReceiptScanOutboxRepository outboxRepository,
                                  ReceiptScanJobDispatcher jobDispatcher) {
        this.regexParser = regexParser;
        this.geminiParser = geminiParser;
        this.receiptRepository = receiptRepository;
        this.imageService = imageService;
        this.securityService = securityService;
        this.categoryRepository = categoryRepository;
        this.outboxRepository = outboxRepository;
        this.jobDispatcher = jobDispatcher;
    }

    @Override
    @RequiresPlan(SubscriptionPlan.PREMIUM)
    @Transactional
    public ReceiptResponse startScan(byte[] imageBytes, String filename, String mediaType) {
        User user = securityService.getCurrentUser();
        Long userId = user.getId();

        Image image = imageService.save(user, filename, mediaType, imageBytes);

        Receipt receipt = Receipt.builder()
                .user(user)
                .image(image)
                .status(ReceiptScanStatus.PENDING)
                .build();
        receipt = receiptRepository.save(receipt);

        ReceiptScanOutbox outbox = ReceiptScanOutbox.builder()
                .receiptId(receipt.getId())
                .bucket(image.getBucketName())
                .objectKey(image.getObjectKey())
                .state(ReceiptScanOutboxState.PENDING)
                .createdAt(OffsetDateTime.now(user.getTimeZone().toZoneId()))
                .build();
        outboxRepository.save(outbox);

        publishScanJobAfterCommit(outbox.getId());

        log.info("scan_enqueued receipt_id={} userId={} bucket={} objectKey={}",
                receipt.getId(), userId, image.getBucketName(), image.getObjectKey());
        return toResponse(receipt);
    }

    private void publishScanJobAfterCommit(Long outboxId) {
        Runnable publish = () -> {
            try {
                jobDispatcher.dispatch(outboxId);
            } catch (RuntimeException ex) {
                log.warn("scan_job_immediate_publish_failed outbox_id={}; el scheduler lo reintentará",
                        outboxId, ex);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }

    @Override
    @Transactional
    public void applyScanResult(ReceiptScanResultMessage message) {
        Receipt receipt = receiptRepository.findByIdForUpdate(message.receiptId()).orElse(null);
        if (receipt == null) {
            log.warn("scan_result_unknown_receipt receipt_id={}", message.receiptId());
            return;
        }
        if (isTerminal(receipt.getStatus())) {
            log.debug("scan_result_ignored receipt_id={} status={}", receipt.getId(), receipt.getStatus());
            return;
        }

        receipt.setRawOcrText(message.rawText());
        receipt.setOcrDurationMs(message.durationMs());

        if (!"DONE".equalsIgnoreCase(message.status())) {
            fail(receipt, message.error() != null ? message.error() : "El procesamiento OCR falló");
            return;
        }

        String rawText = message.rawText();
        ReceiptData data = regexParser.parse(rawText);
        String engineUsed = "REGEX";

        if (data.parseConfidence() < CONFIDENCE_THRESHOLD) {
            try {
                ReceiptData improved = geminiParser.parse(rawText);
                if (improved.parseConfidence() > data.parseConfidence()) {
                    data = improved;
                    engineUsed = "GEMINI";
                }
            } catch (RuntimeException ex) {
                log.warn("gemini_fallback_failed receipt_id={}", receipt.getId(), ex);
            }
        }

        if (data.amount() == null) {
            fail(receipt, "No se detectó el monto en el recibo");
            return;
        }

        receipt.setDetectedAmount(data.amount());
        receipt.setDetectedDate(data.date());
        receipt.setDescription(data.description());
        receipt.setPaymentMethod(data.paymentMethod());
        receipt.setTransactionType(data.transactionType());
        receipt.setCurrencyGuess(data.currencyGuess());
        receipt.setConfidenceScore(data.parseConfidence());
        receipt.setParserEngineUsed(engineUsed);
        receipt.setDetectedCategoryId(matchCategory(receipt.getUser().getId(), data, rawText));
        receipt.setStatus(ReceiptScanStatus.DONE);
        receipt.setErrorMessage(null);
        receiptRepository.save(receipt);

        log.info("scan_completed receipt_id={} engine={} amount={} duration_ms={}",
                receipt.getId(), engineUsed, data.amount(), message.durationMs());
    }

    @Override
    @Transactional
    public void markFailed(Long receiptId, String reason) {
        Receipt receipt = receiptRepository.findByIdForUpdate(receiptId).orElse(null);
        if (receipt == null || isTerminal(receipt.getStatus())) {
            return;
        }
        fail(receipt, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptResponse getScanResult(Long receiptId) {
        Long userId = securityService.getCurrentUser().getId();
        Receipt receipt = receiptRepository.findByIdAndUser_Id(receiptId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Recibo no encontrado: " + receiptId));
        return toResponse(receipt);
    }

    @Override
    @Transactional
    public ReceiptResponse improveWithAi(Long receiptId) {
        Long userId = securityService.getCurrentUser().getId();
        Receipt receipt = receiptRepository.findByIdAndUser_Id(receiptId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Recibo no encontrado: " + receiptId));

        String rawText = receipt.getRawOcrText();
        if (rawText == null || rawText.isBlank()) {
            throw new OcrProcessingException("El recibo no tiene texto OCR para mejorar");
        }

        double oldConfidence = receipt.getConfidenceScore() != null ? receipt.getConfidenceScore() : 0.0;
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

        return toResponse(receipt);
    }

    private void fail(Receipt receipt, String reason) {
        receipt.setStatus(ReceiptScanStatus.FAILED);
        receipt.setErrorMessage(reason);
        receiptRepository.save(receipt);
        log.warn("scan_failed receipt_id={} error={}", receipt.getId(), reason);
    }

    private static boolean isTerminal(ReceiptScanStatus status) {
        return status == ReceiptScanStatus.DONE || status == ReceiptScanStatus.FAILED;
    }

    private ReceiptResponse toResponse(Receipt receipt) {
        Long imageId = receipt.getImage() != null ? receipt.getImage().getId() : null;
        return new ReceiptResponse(
                receipt.getId(),
                receipt.getStatus(),
                receipt.getErrorMessage(),
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
}
