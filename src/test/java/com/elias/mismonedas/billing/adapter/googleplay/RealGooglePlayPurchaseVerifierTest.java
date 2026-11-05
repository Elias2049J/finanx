package com.elias.mismonedas.billing.adapter.googleplay;

import com.elias.mismonedas.billing.config.GooglePlayProperties;
import com.elias.mismonedas.billing.domain.enums.BillingCycle;
import com.elias.mismonedas.billing.domain.enums.GooglePlayPurchaseState;
import com.elias.mismonedas.billing.domain.model.GooglePlaySubscription;
import com.elias.mismonedas.billing.domain.model.GooglePlaySubscriptionLineItem;
import com.elias.mismonedas.billing.domain.model.VerifiedGooglePlayPurchase;
import com.elias.mismonedas.billing.dto.googleplay.GooglePlayVerificationRequest;
import com.elias.mismonedas.billing.port.GooglePlayApiClient;
import com.elias.mismonedas.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealGooglePlayPurchaseVerifierTest {

    private static final String PACKAGE = "com.elias.mismonedas";
    private static final String MONTHLY = "premium_monthly";
    private static final String ANNUAL = "premium_annual";
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    private static final OffsetDateTime END = START.plusMonths(1);

    private final GooglePlayApiClient apiClient = mock(GooglePlayApiClient.class);
    private RealGooglePlayPurchaseVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new RealGooglePlayPurchaseVerifier(properties(), apiClient);
    }

    @Test
    void shouldMapActiveMonthlyPurchaseFromGoogle() {
        when(apiClient.getSubscriptionPurchase(PACKAGE, "token-1")).thenReturn(
                subscription("SUBSCRIPTION_STATE_ACTIVE", List.of(
                        lineItem(MONTHLY, "monthly-plan", END, true,
                                new BigDecimal("25.50"), "USD"))));

        VerifiedGooglePlayPurchase purchase = verifier.verify(request(MONTHLY, "monthly-plan", "token-1"));

        assertThat(purchase.purchaseToken()).isEqualTo("token-1");
        assertThat(purchase.orderId()).isEqualTo("GPA.123");
        assertThat(purchase.productId()).isEqualTo(MONTHLY);
        assertThat(purchase.basePlanId()).isEqualTo("monthly-plan");
        assertThat(purchase.billingCycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(purchase.amount()).isEqualByComparingTo("25.50");
        assertThat(purchase.currencyCode()).isEqualTo("USD");
        assertThat(purchase.state()).isEqualTo(GooglePlayPurchaseState.ACTIVE);
        assertThat(purchase.purchasedAt()).isEqualTo(START);
        assertThat(purchase.expiresAt()).isEqualTo(END);
        assertThat(purchase.autoRenew()).isTrue();
        assertThat(purchase.testMode()).isTrue();
    }

    @Test
    void shouldMapCanceledPurchaseAndKeepConfiguredPriceWhenGoogleOmitsIt() {
        when(apiClient.getSubscriptionPurchase(PACKAGE, "token-2")).thenReturn(
                subscription("SUBSCRIPTION_STATE_CANCELED", List.of(
                        lineItem(ANNUAL, "annual-plan", START.plusYears(1), false,
                                null, null))));

        VerifiedGooglePlayPurchase purchase = verifier.verify(request(ANNUAL, "annual-plan", "token-2"));

        assertThat(purchase.billingCycle()).isEqualTo(BillingCycle.ANNUAL);
        assertThat(purchase.amount()).isEqualByComparingTo("199.00");
        assertThat(purchase.currencyCode()).isEqualTo("PEN");
        assertThat(purchase.state()).isEqualTo(GooglePlayPurchaseState.CANCELED);
        assertThat(purchase.autoRenew()).isFalse();
    }

    @Test
    void shouldTreatGracePeriodAsActiveAndPausedAsPending() {
        when(apiClient.getSubscriptionPurchase(PACKAGE, "grace")).thenReturn(
                subscription("SUBSCRIPTION_STATE_IN_GRACE_PERIOD", List.of(
                        lineItem(MONTHLY, "monthly-plan", END, true, null, null))));
        when(apiClient.getSubscriptionPurchase(PACKAGE, "paused")).thenReturn(
                subscription("SUBSCRIPTION_STATE_PAUSED", List.of(
                        lineItem(MONTHLY, "monthly-plan", END, true, null, null))));

        assertThat(verifier.verify(request(MONTHLY, "monthly-plan", "grace")).state())
                .isEqualTo(GooglePlayPurchaseState.ACTIVE);
        assertThat(verifier.verify(request(MONTHLY, "monthly-plan", "paused")).state())
                .isEqualTo(GooglePlayPurchaseState.PENDING);
    }

    @Test
    void shouldAllowPendingPurchaseWithoutLineItems() {
        when(apiClient.getSubscriptionPurchase(PACKAGE, "pending")).thenReturn(
                subscription("SUBSCRIPTION_STATE_PENDING", List.of()));

        VerifiedGooglePlayPurchase purchase = verifier.verify(
                request(MONTHLY, "monthly-plan", "pending"));

        assertThat(purchase.state()).isEqualTo(GooglePlayPurchaseState.PENDING);
        assertThat(purchase.expiresAt()).isNull();
    }

    @Test
    void shouldRejectLineItemForAnotherProduct() {
        when(apiClient.getSubscriptionPurchase(PACKAGE, "token")).thenReturn(
                subscription("SUBSCRIPTION_STATE_ACTIVE", List.of(
                        lineItem(ANNUAL, "annual-plan", END, true, null, null))));

        assertThatThrownBy(() -> verifier.verify(request(MONTHLY, "monthly-plan", "token")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("producto o plan");
    }

    @Test
    void shouldRejectLineItemForAnotherBasePlan() {
        when(apiClient.getSubscriptionPurchase(PACKAGE, "token")).thenReturn(
                subscription("SUBSCRIPTION_STATE_ACTIVE", List.of(
                        lineItem(MONTHLY, "other-plan", END, true, null, null))));

        assertThatThrownBy(() -> verifier.verify(request(MONTHLY, "monthly-plan", "token")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("producto o plan");
    }

    @Test
    void shouldRejectUnknownStateAndMissingExpiryForActivePurchase() {
        when(apiClient.getSubscriptionPurchase(PACKAGE, "unknown")).thenReturn(
                subscription("SUBSCRIPTION_STATE_UNSPECIFIED", List.of()));
        when(apiClient.getSubscriptionPurchase(PACKAGE, "no-expiry")).thenReturn(
                subscription("SUBSCRIPTION_STATE_ACTIVE", List.of(
                        lineItem(MONTHLY, "monthly-plan", null, true, null, null))));

        assertThatThrownBy(() -> verifier.verify(request(MONTHLY, "monthly-plan", "unknown")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no soportado");
        assertThatThrownBy(() -> verifier.verify(request(MONTHLY, "monthly-plan", "no-expiry")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("expiracion");
    }

    @Test
    void shouldRejectWrongPackageOrUnconfiguredProductBeforeCallingGoogle() {
        GooglePlayVerificationRequest wrongPackage = request(MONTHLY, "monthly-plan", "token");
        wrongPackage.setPackageName("com.example.other");

        assertThatThrownBy(() -> verifier.verify(wrongPackage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("packageName");
        assertThatThrownBy(() -> verifier.verify(request("premium_weekly", null, "token")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }

    private static GooglePlayProperties properties() {
        return new GooglePlayProperties(
                "google",
                PACKAGE,
                MONTHLY,
                ANNUAL,
                new BigDecimal("19.90"),
                new BigDecimal("199.00"),
                "PEN",
                "secrets/google-play.json",
                "https://androidpublisher.googleapis.com/");
    }

    private static GooglePlayVerificationRequest request(
            String productId, String basePlanId, String token) {
        return GooglePlayVerificationRequest.builder()
                .packageName(PACKAGE)
                .productId(productId)
                .basePlanId(basePlanId)
                .purchaseToken(token)
                .build();
    }

    private static GooglePlaySubscription subscription(
            String state, List<GooglePlaySubscriptionLineItem> lineItems) {
        return new GooglePlaySubscription(
                "token", "GPA.123", state, START, true, lineItems);
    }

    private static GooglePlaySubscriptionLineItem lineItem(
            String productId,
            String basePlanId,
            OffsetDateTime expiry,
            boolean autoRenew,
            BigDecimal amount,
            String currency) {
        return new GooglePlaySubscriptionLineItem(
                productId, basePlanId, expiry, autoRenew, amount, currency);
    }
}
