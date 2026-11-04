package com.elias.mismonedas.billing.adapter.googleplay;

import com.elias.mismonedas.billing.config.GooglePlayProperties;
import com.elias.mismonedas.billing.domain.enums.BillingCycle;
import com.elias.mismonedas.billing.domain.enums.GooglePlayPurchaseState;
import com.elias.mismonedas.billing.domain.model.VerifiedGooglePlayPurchase;
import com.elias.mismonedas.billing.dto.googleplay.GooglePlayVerificationRequest;
import com.elias.mismonedas.billing.port.GooglePlayPurchaseVerifier;
import com.elias.mismonedas.shared.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Verificador de desarrollo para probar el flujo completo sin Play Console.
 * <p>
 * Se habilita con {@code app.billing.google-play.mode=fake}. Nunca debe
 * habilitarse en produccion: cualquier token {@code test-valid-*} activa
 * PREMIUM.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.billing.google-play", name = "mode", havingValue = "fake")
public class FakeGooglePlayPurchaseVerifier implements GooglePlayPurchaseVerifier {

    private final GooglePlayProperties properties;

    @Override
    public VerifiedGooglePlayPurchase verify(GooglePlayVerificationRequest request) {
        validatePackage(request);
        validateProduct(request);

        String token = request.getPurchaseToken();
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("purchaseToken is required");
        }

        BillingCycle cycle = request.getProductId().equals(properties.monthlyProductId())
                ? BillingCycle.MONTHLY
                : BillingCycle.ANNUAL;
        BigDecimal amount = cycle == BillingCycle.MONTHLY
                ? properties.monthlyPrice()
                : properties.annualPrice();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime periodEnd = cycle == BillingCycle.MONTHLY
                ? now.plusMonths(1)
                : now.plusYears(1);

        GooglePlayPurchaseState state;
        boolean autoRenew = true;
        OffsetDateTime expiresAt;
        if (token.startsWith("test-pending-")) {
            state = GooglePlayPurchaseState.PENDING;
            expiresAt = periodEnd;
        } else if (token.startsWith("test-expired-")) {
            state = GooglePlayPurchaseState.EXPIRED;
            expiresAt = now.minusDays(1);
        } else if (token.startsWith("test-revoked-")) {
            state = GooglePlayPurchaseState.REVOKED;
            expiresAt = now.minusDays(1);
        } else if (token.startsWith("test-canceled-")) {
            state = GooglePlayPurchaseState.CANCELED;
            autoRenew = false;
            expiresAt = periodEnd;
        } else if (token.startsWith("test-valid-")) {
            state = GooglePlayPurchaseState.ACTIVE;
            expiresAt = periodEnd;
        } else {
            throw new ConflictException("Token de compra de prueba no reconocido");
        }

        String orderId = "GPA.TEST." + UUID.nameUUIDFromBytes(
                (request.getPackageName() + ":" + token).getBytes(StandardCharsets.UTF_8));

        return new VerifiedGooglePlayPurchase(
                token,
                orderId,
                request.getProductId(),
                request.getBasePlanId(),
                cycle,
                amount,
                properties.currency(),
                state,
                now,
                expiresAt,
                autoRenew,
                true
        );
    }

    private void validatePackage(GooglePlayVerificationRequest request) {
        if (properties.packageName() == null || properties.packageName().isBlank()) {
            throw new IllegalStateException("app.billing.google-play.package-name no esta configurado");
        }
        if (!properties.packageName().equals(request.getPackageName())) {
            throw new IllegalArgumentException("packageName no corresponde a esta aplicacion");
        }
    }

    private void validateProduct(GooglePlayVerificationRequest request) {
        boolean monthly = request.getProductId() != null
                && request.getProductId().equals(properties.monthlyProductId());
        boolean annual = request.getProductId() != null
                && request.getProductId().equals(properties.annualProductId());
        if (!monthly && !annual) {
            throw new IllegalArgumentException("productId no configurado para pruebas");
        }
    }
}
