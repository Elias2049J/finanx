package com.elias.mismonedas.billing.adapter.googleplay;

import com.elias.mismonedas.billing.config.GooglePlayProperties;
import com.elias.mismonedas.billing.domain.enums.BillingCycle;
import com.elias.mismonedas.billing.domain.enums.GooglePlayPurchaseState;
import com.elias.mismonedas.billing.domain.model.VerifiedGooglePlayPurchase;
import com.elias.mismonedas.billing.dto.googleplay.GooglePlayVerificationRequest;
import com.elias.mismonedas.shared.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeGooglePlayPurchaseVerifierTest {

    private FakeGooglePlayPurchaseVerifier verifier;

    @BeforeEach
    void setUp() {
        GooglePlayProperties properties = new GooglePlayProperties(
                "fake",
                "com.elias.mismonedas",
                "premium_monthly_test",
                "premium_annual_test",
                new BigDecimal("19.90"),
                new BigDecimal("199.00"),
                "PEN");
        verifier = new FakeGooglePlayPurchaseVerifier(properties);
    }

    @Test
    void shouldVerifyMonthlyPurchase() {
        VerifiedGooglePlayPurchase purchase = verifier.verify(
                request("premium_monthly_test", "monthly-test", "test-valid-monthly"));

        assertThat(purchase.purchaseToken()).isEqualTo("test-valid-monthly");
        assertThat(purchase.productId()).isEqualTo("premium_monthly_test");
        assertThat(purchase.basePlanId()).isEqualTo("monthly-test");
        assertThat(purchase.billingCycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(purchase.amount()).isEqualByComparingTo("19.90");
        assertThat(purchase.currencyCode()).isEqualTo("PEN");
        assertThat(purchase.state()).isEqualTo(GooglePlayPurchaseState.ACTIVE);
        assertThat(purchase.autoRenew()).isTrue();
        assertThat(purchase.testMode()).isTrue();
        assertThat(purchase.orderId()).startsWith("GPA.TEST.");
        assertThat(purchase.expiresAt()).isAfter(purchase.purchasedAt());
    }

    @Test
    void shouldVerifyAnnualPurchaseWithAnnualPrice() {
        VerifiedGooglePlayPurchase purchase = verifier.verify(
                request("premium_annual_test", "annual-test", "test-valid-annual"));

        assertThat(purchase.billingCycle()).isEqualTo(BillingCycle.ANNUAL);
        assertThat(purchase.amount()).isEqualByComparingTo("199.00");
        assertThat(purchase.expiresAt()).isAfter(purchase.purchasedAt().plusMonths(11));
    }

    @Test
    void shouldMapCanceledPurchaseWithoutAutoRenew() {
        VerifiedGooglePlayPurchase purchase = verifier.verify(
                request("premium_monthly_test", "monthly-test", "test-canceled-monthly"));

        assertThat(purchase.state()).isEqualTo(GooglePlayPurchaseState.CANCELED);
        assertThat(purchase.autoRenew()).isFalse();
    }

    @Test
    void shouldUseAnnualPeriodForCanceledAnnualPurchase() {
        VerifiedGooglePlayPurchase purchase = verifier.verify(
                request("premium_annual_test", "annual-test", "test-canceled-annual"));

        assertThat(purchase.state()).isEqualTo(GooglePlayPurchaseState.CANCELED);
        assertThat(purchase.autoRenew()).isFalse();
        assertThat(purchase.expiresAt()).isAfter(purchase.purchasedAt().plusMonths(11));
    }

    @Test
    void shouldMapPendingExpiredAndRevokedPurchases() {
        assertThat(verifier.verify(request("premium_monthly_test", "monthly-test", "test-pending-1")).state())
                .isEqualTo(GooglePlayPurchaseState.PENDING);
        assertThat(verifier.verify(request("premium_monthly_test", "monthly-test", "test-expired-1")).state())
                .isEqualTo(GooglePlayPurchaseState.EXPIRED);
        assertThat(verifier.verify(request("premium_monthly_test", "monthly-test", "test-revoked-1")).state())
                .isEqualTo(GooglePlayPurchaseState.REVOKED);
    }

    @Test
    void shouldRejectUnknownToken() {
        assertThatThrownBy(() -> verifier.verify(
                request("premium_monthly_test", "monthly-test", "unknown-token")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no reconocido");
    }

    @Test
    void shouldRejectWrongPackage() {
        GooglePlayVerificationRequest request = request("premium_monthly_test", "monthly-test", "test-valid");
        request.setPackageName("com.example.other");

        assertThatThrownBy(() -> verifier.verify(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("packageName");
    }

    @Test
    void shouldRejectUnknownProduct() {
        assertThatThrownBy(() -> verifier.verify(
                request("premium_weekly_test", "weekly-test", "test-valid")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }

    private static GooglePlayVerificationRequest request(String productId, String basePlanId, String token) {
        return GooglePlayVerificationRequest.builder()
                .packageName("com.elias.mismonedas")
                .productId(productId)
                .basePlanId(basePlanId)
                .purchaseToken(token)
                .build();
    }
}
