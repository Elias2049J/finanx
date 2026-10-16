package com.elias.mismonedas.billing.controller;

import com.elias.mismonedas.billing.dto.subscription.SubscriptionRequest;
import com.elias.mismonedas.billing.dto.subscription.SubscriptionResponse;
import com.elias.mismonedas.billing.domain.enums.SubscriptionState;
import com.elias.mismonedas.billing.service.BillingService;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final BillingService billingService;

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
        return ResponseEntity.created(URI.create("/api/v1/subscriptions/" + saved.getId())).body(saved);
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
            description = "Lists subscriptions of the authenticated user with optional state and pagination/sort parameters.",
            operationId = "listSubscriptions",
            tags = {"Subscriptions"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "List retrieved"),
                    @ApiResponse(responseCode = "404", description = "User not found")
            }
    )
    @GetMapping
    public ResponseEntity<PageResponse<SubscriptionResponse>> listSubscriptions(
            @RequestParam(required = false) SubscriptionState state,
            @ParameterObject @PageableDefault(size = PaginationDefaults.DEFAULT_PAGE_SIZE, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(billingService.list(state, pageable));
    }

    @Operation(
            summary = "Get current subscription",
            description = "Returns the active subscription of the authenticated user.",
            operationId = "getCurrentSubscription",
            tags = {"Subscriptions"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Active subscription found"),
                    @ApiResponse(responseCode = "404", description = "No active subscription")
            }
    )
    @GetMapping("/current")
    public ResponseEntity<SubscriptionResponse> getCurrentSubscription() {
        return ResponseEntity.ok(billingService.getCurrentSubscription());
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
