package com.elias.mismonedas.billing.adapter.googleplay;

import com.elias.mismonedas.billing.config.GooglePlayProperties;
import com.elias.mismonedas.billing.domain.model.GooglePlaySubscription;
import com.elias.mismonedas.shared.exception.ConflictException;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GooglePlayApiClientImplTest {

    private static final String PACKAGE = "com.elias.mismonedas";
    private static final String TOKEN = "purchase-token";
    private static final String URL =
            "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
                    + PACKAGE + "/purchases/subscriptionsv2/tokens/" + TOKEN;

    private MockRestServiceServer server;
    private GooglePlayApiClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://androidpublisher.googleapis.com/");
        server = MockRestServiceServer.bindTo(builder).build();
        GoogleCredentials credentials = GoogleCredentials.create(new AccessToken(
                "test-access-token", Date.from(Instant.now().plusSeconds(3600))));
        client = new GooglePlayApiClientImpl(properties(), credentials, builder.build());
    }

    @Test
    void shouldCallAndroidPublisherAndMapSubscriptionResponse() {
        server.expect(requestTo(URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-access-token"))
                .andRespond(withSuccess("""
                        {
                          "startTime": "2026-01-01T00:00:00Z",
                          "subscriptionState": "SUBSCRIPTION_STATE_ACTIVE",
                          "latestOrderId": "GPA.123",
                          "lineItems": [{
                            "productId": "premium_monthly",
                            "expiryTime": "2026-02-01T00:00:00Z",
                            "autoRenewingPlan": {
                              "autoRenewEnabled": true,
                              "recurringPrice": {
                                "currencyCode": "USD",
                                "units": "25",
                                "nanos": 500000000
                              }
                            },
                            "offerDetails": { "basePlanId": "monthly-plan" }
                          }],
                          "testPurchase": true
                        }
                        """, MediaType.APPLICATION_JSON));

        GooglePlaySubscription subscription = client.getSubscriptionPurchase(PACKAGE, TOKEN);

        assertThat(subscription.purchaseToken()).isEqualTo(TOKEN);
        assertThat(subscription.latestOrderId()).isEqualTo("GPA.123");
        assertThat(subscription.subscriptionState()).isEqualTo("SUBSCRIPTION_STATE_ACTIVE");
        assertThat(subscription.startTime())
                .isEqualTo(java.time.OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        assertThat(subscription.testPurchase()).isTrue();
        assertThat(subscription.lineItems()).hasSize(1);
        assertThat(subscription.lineItems().getFirst().productId()).isEqualTo("premium_monthly");
        assertThat(subscription.lineItems().getFirst().basePlanId()).isEqualTo("monthly-plan");
        assertThat(subscription.lineItems().getFirst().expiryTime())
                .isEqualTo(java.time.OffsetDateTime.parse("2026-02-01T00:00:00Z"));
        assertThat(subscription.lineItems().getFirst().autoRenewEnabled()).isTrue();
        assertThat(subscription.lineItems().getFirst().priceAmount())
                .isEqualByComparingTo("25.5");
        assertThat(subscription.lineItems().getFirst().priceCurrency()).isEqualTo("USD");
        server.verify();
    }

    @Test
    void shouldMapGoogleClientErrorsToConflict() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.getSubscriptionPurchase(PACKAGE, TOKEN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no valido");
    }

    @Test
    void shouldMapServerErrorsToUnavailable() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.getSubscriptionPurchase(PACKAGE, TOKEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no esta disponible");
    }

    private static GooglePlayProperties properties() {
        return new GooglePlayProperties(
                "google",
                PACKAGE,
                "premium_monthly",
                "premium_annual",
                new java.math.BigDecimal("19.90"),
                new java.math.BigDecimal("199.00"),
                "PEN",
                "secrets/google-play.json",
                "https://androidpublisher.googleapis.com/");
    }
}
