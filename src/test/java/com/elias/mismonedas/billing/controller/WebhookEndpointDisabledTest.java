package com.elias.mismonedas.billing.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.messaging.rabbitmq.consumer-auto-startup=false",
        "app.billing.webhooks.enabled=false"
})
@AutoConfigureMockMvc
class WebhookEndpointDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnNotFoundWhenWebhooksAreDisabled() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Signature", "invalid")
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
