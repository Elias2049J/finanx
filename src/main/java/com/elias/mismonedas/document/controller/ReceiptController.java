package com.elias.mismonedas.document.controller;

import com.elias.mismonedas.document.dto.ReceiptResponse;
import com.elias.mismonedas.document.service.ReceiptScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
@Tag(name = "Receipts", description = "Escaneo OCR de recibos")
public class ReceiptController {

    private final ReceiptScanService receiptScanService;

    @Operation(
            summary = "Encolar escaneo OCR de recibo",
            description = "Sube la imagen, crea el recibo en estado PENDING y encola el OCR en el microservicio doc-processing. " +
                    "Responde 202 con la Location para consultar el resultado. Requiere plan PREMIUM.",
            operationId = "scanReceipt",
            tags = {"Receipts"},
            responses = {
                    @ApiResponse(responseCode = "202", description = "Escaneo encolado; consultar estado en Location"),
                    @ApiResponse(responseCode = "400", description = "Imagen invalida o muy grande"),
                    @ApiResponse(responseCode = "403", description = "Plan no suficiente (PLAN_REQUIRED)")
            }
    )
    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptResponse> scan(
            @Parameter(description = "Imagen del recibo (max 5MB)") @RequestParam("image") MultipartFile image) {

        if (image.isEmpty()) {
            throw new IllegalArgumentException("La imagen no puede estar vacía");
        }
        if (image.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("La imagen no debe superar 5MB");
        }

        byte[] imageBytes;
        try {
            imageBytes = image.getBytes();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read uploaded receipt image", ex);
        }

        ReceiptResponse result = receiptScanService.startScan(
                imageBytes, image.getOriginalFilename(), image.getContentType());

        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/receipts/{id}")
                .buildAndExpand(result.getReceiptId())
                .toUri();

        return ResponseEntity.accepted().location(location).body(result);
    }

    @Operation(
            summary = "Mejorar parseo con IA",
            description = "Re-ejecuta GeminiParser sobre el texto OCR guardado. Actualiza el recibo solo si la confianza mejora.",
            operationId = "improveScan",
            tags = {"Receipts"}
    )
    @PostMapping("/{receiptId}/improve")
    public ResponseEntity<ReceiptResponse> improve(
            @Parameter(description = "ID del recibo") @PathVariable Long receiptId) {
        return ResponseEntity.ok(receiptScanService.improveWithAi(receiptId));
    }

    @Operation(
            summary = "Obtener resultado de escaneo",
            description = "Recupera el resultado de un escaneo previo por ID.",
            operationId = "getScanResult",
            tags = {"Receipts"}
    )
    @GetMapping("/{receiptId}")
    public ResponseEntity<ReceiptResponse> getScanResult(
            @PathVariable Long receiptId) {
        return ResponseEntity.ok(receiptScanService.getById(receiptId));
    }
}
