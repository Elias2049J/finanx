package com.elias.mismonedas.insights.dto.analytics.component;

import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TimeSeriesPointContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldNotExposeTransactionContextInTimelinePoints() throws Exception {
        TimeSeriesPoint<TransactionResponse> point = new TimeSeriesPoint<>(
                LocalDate.of(2026, 6, 1),
                new BigDecimal("10.00"),
                3);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(point));

        assertThat(json.has("context")).isFalse();
        assertThat(json.get("date").asText()).isEqualTo("2026-06-01");
        assertThat(json.get("amount").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(json.get("occurrenceCount").asLong()).isEqualTo(3);
    }
}
