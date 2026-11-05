package com.elias.mismonedas.billing.adapter.googleplay;

import com.elias.mismonedas.billing.config.GooglePlayProperties;
import com.elias.mismonedas.billing.domain.model.GooglePlaySubscription;
import com.elias.mismonedas.billing.domain.model.GooglePlaySubscriptionLineItem;
import com.elias.mismonedas.billing.port.GooglePlayApiClient;
import com.elias.mismonedas.shared.exception.ConflictException;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente real de Android Publisher API v3. Solo se crea con
 * {@code app.billing.google-play.mode=google}; si faltan las credenciales el
 * arranque falla en lugar de aceptar compras sin verificar.
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.billing.google-play", name = "mode", havingValue = "google")
public class GooglePlayApiClientImpl implements GooglePlayApiClient {

    private static final String ANDROID_PUBLISHER_SCOPE =
            "https://www.googleapis.com/auth/androidpublisher";
    private static final String SUBSCRIPTION_PATH =
            "/androidpublisher/v3/applications/{packageName}/purchases/subscriptionsv2/tokens/{token}";

    private final GooglePlayProperties properties;
    private final GoogleCredentials credentials;
    private final RestClient restClient;

    public GooglePlayApiClientImpl(GooglePlayProperties properties) {
        this(properties, loadCredentials(properties), buildRestClient(properties));
    }

    GooglePlayApiClientImpl(
            GooglePlayProperties properties,
            GoogleCredentials credentials,
            RestClient restClient) {
        this.properties = properties;
        this.credentials = credentials;
        this.restClient = restClient;
    }

    private static GoogleCredentials loadCredentials(GooglePlayProperties properties) {
        String credentialsPath = properties.serviceAccountCredentialsPath();
        if (credentialsPath == null || credentialsPath.isBlank()) {
            throw new IllegalStateException(
                    "APP_BILLING_GOOGLE_PLAY_SERVICE_ACCOUNT_PATH es obligatoria con mode=google");
        }
        Path path = Path.of(credentialsPath);
        if (!Files.isReadable(path)) {
            throw new IllegalStateException(
                    "No se puede leer el service account de Google Play: " + credentialsPath);
        }
        try (InputStream input = Files.newInputStream(path)) {
            return GoogleCredentials.fromStream(input).createScoped(ANDROID_PUBLISHER_SCOPE);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Service account de Google Play invalido: " + credentialsPath, ex);
        }
    }

    private static RestClient buildRestClient(GooglePlayProperties properties) {
        String baseUrl = properties.apiBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "APP_BILLING_GOOGLE_PLAY_API_BASE_URL es obligatoria con mode=google");
        }
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public GooglePlaySubscription getSubscriptionPurchase(String packageName, String purchaseToken) {
        try {
            String accessToken = accessToken();
            JsonNode response = restClient.get()
                    .uri(SUBSCRIPTION_PATH, packageName, purchaseToken)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || response.isMissingNode() || response.isNull()) {
                throw new ConflictException("Google Play no devolvio datos para la compra");
            }
            return map(response, purchaseToken);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 400 || status == 401 || status == 403 || status == 404) {
                throw new ConflictException("Token de compra de Google Play no valido");
            }
            log.warn("Android Publisher respondio {} al verificar una compra", status);
            throw new IllegalStateException("Google Play no esta disponible en este momento", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo autenticar contra Google Play", ex);
        }
    }

    private String accessToken() throws IOException {
        credentials.refreshIfExpired();
        AccessToken token = credentials.getAccessToken();
        if (token == null) {
            credentials.refresh();
            token = credentials.getAccessToken();
        }
        if (token == null || token.getTokenValue() == null || token.getTokenValue().isBlank()) {
            throw new IOException("GoogleCredentials no emitio un access token");
        }
        return token.getTokenValue();
    }

    private GooglePlaySubscription map(JsonNode root, String purchaseToken) {
        List<GooglePlaySubscriptionLineItem> lineItems = new ArrayList<>();
        JsonNode items = root.path("lineItems");
        if (items.isArray()) {
            for (JsonNode item : items) {
                lineItems.add(mapLineItem(item));
            }
        }
        return new GooglePlaySubscription(
                purchaseToken,
                textOrNull(root, "latestOrderId"),
                textOrNull(root, "subscriptionState"),
                dateOrNull(root.path("startTime")),
                root.path("testPurchase").asBoolean(false),
                List.copyOf(lineItems)
        );
    }

    private GooglePlaySubscriptionLineItem mapLineItem(JsonNode item) {
        JsonNode autoRenewingPlan = item.path("autoRenewingPlan");
        JsonNode recurringPrice = autoRenewingPlan.path("recurringPrice");
        return new GooglePlaySubscriptionLineItem(
                textOrNull(item, "productId"),
                textOrNull(item.path("offerDetails"), "basePlanId"),
                dateOrNull(item.path("expiryTime")),
                autoRenewingPlan.path("autoRenewEnabled").asBoolean(false),
                moneyAmount(recurringPrice),
                textOrNull(recurringPrice, "currencyCode")
        );
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private static OffsetDateTime dateOrNull(JsonNode node) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(node.asText());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static BigDecimal moneyAmount(JsonNode money) {
        if (money == null || money.isMissingNode() || money.isNull()) {
            return null;
        }
        String units = money.path("units").asText("0");
        int nanos = money.path("nanos").asInt(0);
        try {
            return new BigDecimal(units).add(BigDecimal.valueOf(nanos, 9));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
