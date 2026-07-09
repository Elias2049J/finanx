package com.elias.mismonedas.service.impl;

import com.elias.mismonedas.dto.payment.ReceiptResponse;
import com.elias.mismonedas.entity.Receipt;
import com.elias.mismonedas.entity.User;
import com.elias.mismonedas.entity.enums.SubscriptionPlan;
import com.elias.mismonedas.plan.gating.PlanSubject;
import com.elias.mismonedas.plan.gating.RequiresPlan;
import com.elias.mismonedas.port.OcrPort;
import com.elias.mismonedas.port.OcrResult;
import com.elias.mismonedas.port.ReceiptData;
import com.elias.mismonedas.port.ReceiptParserPort;
import com.elias.mismonedas.repository.ReceiptRepository;
import com.elias.mismonedas.repository.UserRepository;
import com.elias.mismonedas.service.ReceiptScanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ReceiptScanServiceImpl implements ReceiptScanService {

    private static final double CONFIDENCE_THRESHOLD = 0.55;

    private final OcrPort ocrPort;
    private final ReceiptParserPort regexParser;
    private final ReceiptParserPort geminiParser;
    private final ReceiptRepository receiptRepository;
    private final UserRepository userRepository;

    public ReceiptScanServiceImpl(OcrPort ocrPort,
                                  @Qualifier("regex") ReceiptParserPort regexParser,
                                  @Qualifier("gemini") ReceiptParserPort geminiParser,
                                  ReceiptRepository receiptRepository,
                                  UserRepository userRepository) {
        this.ocrPort = ocrPort;
        this.regexParser = regexParser;
        this.geminiParser = geminiParser;
        this.receiptRepository = receiptRepository;
        this.userRepository = userRepository;
    }

    @Override
    @RequiresPlan(SubscriptionPlan.PREMIUM)
    @Transactional
    public ReceiptResponse scanReceipt(@PlanSubject Long userId, byte[] imageBytes) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + userId));

        OcrResult ocrResult = ocrPort.extractText(imageBytes);

        ReceiptData data = regexParser.parse(ocrResult.rawText());
        String engineUsed = "REGEX";
        if (data.parseConfidence() < CONFIDENCE_THRESHOLD) {
            log.debug("Confianza baja ({}), escalando a Gemini", data.parseConfidence());
            try {
                data = geminiParser.parse(ocrResult.rawText());
                engineUsed = "GEMINI";
            } catch (Exception e) {
                log.warn("Fallback Gemini falló, usando resultado de regex", e);
            }
        }

        Receipt receipt = Receipt.builder()
                .user(user)
                .rawOcrText(ocrResult.rawText())
                .detectedAmount(data.amount())
                .detectedDate(data.detectedDate())
                .detectedMerchant(data.merchantName())
                .confidenceScore(data.parseConfidence())
                .parserEngineUsed(engineUsed)
                .ocrDurationMs(ocrResult.durationMs())
                .build();

        receipt = receiptRepository.save(receipt);

        log.info("scan_complete receipt_id={} userId={} engine={} amount={} duration_ms={}",
                receipt.getId(), userId, engineUsed, data.amount(), ocrResult.durationMs());

        return new ReceiptResponse(
                receipt.getId(),
                data.amount(),
                data.detectedDate(),
                data.merchantName(),
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

        return new ReceiptResponse(
                receipt.getId(),
                receipt.getDetectedAmount(),
                receipt.getDetectedDate(),
                receipt.getDetectedMerchant(),
                "PEN",
                receipt.getConfidenceScore(),
                receipt.getParserEngineUsed()
        );
    }
}
