package com.elias.mismonedas.billing.controller;

import com.elias.mismonedas.billing.dto.subscription.SubscriptionRequest;
import com.elias.mismonedas.billing.dto.subscription.SubscriptionResponse;
import com.elias.mismonedas.billing.service.BillingService;
import com.elias.mismonedas.identity.security.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final BillingService billingService;
    private final SecurityService securityService;

    @Operation(
            summary = "Create subscription",
            description = "Creates a new subscription in PENDING state, awaiting payment confirmation.",
            operationId = "createSubscription",
            tags = {"Subscriptions"},
            responses = {
                    @ApiResponse(responseCode = "201", description = "Created"),
                    @ApiResponse(responseCode = "400", description = "Invalid data")
            }
    )
    @PostMapping
    public ResponseEntity<SubscriptionResponse> subscribe(
            @Parameter(description = "Subscription data") @Valid @RequestBody SubscriptionRequest request) {
        SubscriptionResponse saved = billingService.subscribe(request);
        return ResponseEntity.created(URI.create("/subscriptions/" + saved.getId())).body(saved);
    }

    @Operation(
            summary = "Find subscription",
            description = "Gets a subscription by id.",
            operationId = "findSubscriptionById",
            tags = {"Subscriptions"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Found"),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<SubscriptionResponse> findById(
            @Parameter(description = "Subscription id") @PathVariable Long id) {
        return ResponseEntity.ok(billingService.findById(id));
    }

    @Operation(
            summary = "List subscriptions by user",
            description = "Gets all subscriptions belonging to the authenticated user.",
            operationId = "findAllSubscriptionsByUser",
            tags = {"Subscriptions"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "List retrieved"),
                    @ApiResponse(responseCode = "404", description = "User not found")
            }
    )
    @GetMapping("/me")
    public ResponseEntity<List<SubscriptionResponse>> findAllByUser() {
        return ResponseEntity.ok(billingService.findAllByUser(securityService.getCurrentUser().getId()));
    }

    @Operation(
            summary = "Activate subscription",
            description = "Activates a PENDING or PAST_DUE subscription and opens a new billing period.",
            operationId = "activateSubscription",
            tags = {"Subscriptions"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Activated"),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "409", description = "Invalid state transition")
            }
    )
    @PostMapping("/{id}/activate")
    public ResponseEntity<SubscriptionResponse> activate(
            @Parameter(description = "Subscription id") @PathVariable Long id) {
        return ResponseEntity.ok(billingService.activate(id));
    }

    @Operation(
            summary = "Renew subscription",
            description = "Opens the next billing period for an active subscription.",
            operationId = "renewSubscription",
            tags = {"Subscriptions"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Renewed"),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    @PostMapping("/{id}/renew")
    public ResponseEntity<SubscriptionResponse> renew(
            @Parameter(description = "Subscription id") @PathVariable Long id) {
        return ResponseEntity.ok(billingService.renew(id));
    }

    @Operation(
            summary = "Cancel subscription",
            description = "Cancels a subscription and disables auto renew.",
            operationId = "cancelSubscription",
            tags = {"Subscriptions"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Canceled"),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    @PostMapping("/{id}/cancel")
    public ResponseEntity<SubscriptionResponse> cancel(
            @Parameter(description = "Subscription id") @PathVariable Long id) {
        return ResponseEntity.ok(billingService.cancel(id));
    }
}
