package com.elias.mismonedas.billing.controller;

import com.elias.mismonedas.billing.dto.googleplay.GooglePlayVerificationRequest;
import com.elias.mismonedas.billing.dto.googleplay.GooglePlayVerificationResponse;
import com.elias.mismonedas.billing.service.GooglePlayBillingService;
import com.elias.mismonedas.shared.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscriptions/google-play")
@RequiredArgsConstructor
public class GooglePlayBillingController {

    private final GooglePlayBillingService googlePlayBillingService;

    @Operation(
            summary = "Verify Google Play purchase",
            description = "Verifies a Google Play purchase token and activates or renews the user's PREMIUM subscription.",
            operationId = "verifyGooglePlayPurchase",
            tags = {"Subscriptions"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Purchase verified or pending"),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Purchase rejected, revoked, expired or already owned by another user",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @PostMapping("/verify")
    public ResponseEntity<GooglePlayVerificationResponse> verify(
            @Parameter(description = "Google Play purchase data")
            @Valid @RequestBody GooglePlayVerificationRequest request) {
        return ResponseEntity.ok(googlePlayBillingService.verify(request));
    }
}
