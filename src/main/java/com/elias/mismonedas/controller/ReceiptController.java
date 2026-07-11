package com.elias.mismonedas.controller;

import com.elias.mismonedas.dto.payment.ReceiptResponse;
import com.elias.mismonedas.entity.Image;
import com.elias.mismonedas.service.ReceiptScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/receipts")
@RequiredArgsConstructor
@Tag(name = "Receipts", description = "Escaneo OCR de recibos")
public class ReceiptController {

    private final ReceiptScanService receiptScanService;

    @Operation(
            summary = "Escanear recibo con OCR",
            description = "Procesa una imagen de recibo, extrae texto via Tesseract y parsea los datos (monto, fecha, comercio). " +
                    "Requiere plan PREMIUM.",
            operationId = "scanReceipt",
            tags = {"Receipts"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Escaneo completado"),
                    @ApiResponse(responseCode = "400", description = "Imagen invalida o muy grande"),
                    @ApiResponse(responseCode = "403", description = "Plan no suficiente (PLAN_REQUIRED)"),
                    @ApiResponse(responseCode = "503", description = "Servicio ocupado (OcrBusyException)")
            }
    )
    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptResponse> scan(
            @Parameter(description = "ID del usuario") @RequestParam Long userId,
            @Parameter(description = "Imagen del recibo (max 5MB)") @RequestParam("image") MultipartFile image)
            throws IOException {

        if (image.isEmpty()) {
            throw new IllegalArgumentException("La imagen no puede estar vacía");
        }
        if (image.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("La imagen no debe superar 5MB");
        }

        ReceiptResponse result = receiptScanService.scanReceipt(userId, image.getBytes(), image.getOriginalFilename(), image.getContentType());
        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "Mejorar parseo con IA",
            description = "Re-ejecuta GeminiParser sobre el texto OCR guardado. Actualiza el recibo solo si la confianza mejora.",
            operationId = "improveScan",
            tags = {"Receipts"}
    )
    @PostMapping("/{receiptId}/improve")
    public ResponseEntity<ReceiptResponse> improve(
            @Parameter(description = "ID del recibo") @PathVariable Long receiptId,
            @Parameter(description = "ID del usuario") @RequestParam Long userId) {
        return ResponseEntity.ok(receiptScanService.improveWithAi(receiptId, userId));
    }

    @Operation(
            summary = "Obtener resultado de escaneo",
            description = "Recupera el resultado de un escaneo previo por ID.",
            operationId = "getScanResult",
            tags = {"Receipts"}
    )
    @GetMapping("/{receiptId}")
    public ResponseEntity<ReceiptResponse> getScanResult(
            @Parameter(description = "ID del usuario") @RequestParam Long userId,
            @PathVariable Long receiptId) {
        return ResponseEntity.ok(receiptScanService.getScanResult(userId, receiptId));
    }

    @Operation(
            summary = "Obtener imagen del recibo",
            description = "Retorna los bytes de la imagen asociada a un recibo.",
            operationId = "getReceiptImage",
            tags = {"Receipts"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Imagen encontrada"),
                    @ApiResponse(responseCode = "404", description = "Recibo o imagen no encontrada")
            }
    )
    @GetMapping("/{receiptId}/image")
    public ResponseEntity<byte[]> getReceiptImage(
            @Parameter(description = "ID del usuario") @RequestParam Long userId,
            @Parameter(description = "ID del recibo") @PathVariable Long receiptId) {

        Image image = receiptScanService.getReceiptImage(userId, receiptId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getMediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + image.getFilename() + "\"")
                .contentLength(image.getData().length)
                .body(image.getData());
    }
}
