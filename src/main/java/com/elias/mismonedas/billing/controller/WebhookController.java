package com.elias.mismonedas.billing.controller;

import com.elias.mismonedas.billing.domain.enums.PaymentProvider;
import com.elias.mismonedas.billing.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint de webhooks de proveedores de pago.
 * <p>
 * Solo se registra cuando {@code app.billing.webhooks.enabled=true}. Con el gateway manual
 * no existe ningun proveedor que pueda enviar webhooks y el adaptador rechaza todo, asi que
 * por defecto el endpoint no se expone (404). Al integrar un proveedor real hay que
 * implementar {@code PaymentPort.verifyWebhookSignature} y {@code parseWebhookPayload}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.billing.webhooks.enabled", havingValue = "true")
public class WebhookController {

    private final PaymentService paymentService;

    @Operation(
            summary = "Receive provider webhook",
            description = "Receives an inbound webhook notification from a payment provider.",
            operationId = "receiveWebhook",
            tags = {"Webhooks"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Accepted (duplicates are idempotent)"),
                    @ApiResponse(responseCode = "401", description = "Invalid signature")
            }
    )
    @PostMapping("/{provider}")
    public ResponseEntity<Void> receive(
            @Parameter(description = "Payment provider") @PathVariable PaymentProvider provider,
            @Parameter(description = "Signature header sent by the provider")
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String rawPayload) {
        log.info("Webhook received provider={}", provider);
        paymentService.receiveWebhookEvent(provider, rawPayload, signature);
        return ResponseEntity.ok().build();
    }
}
