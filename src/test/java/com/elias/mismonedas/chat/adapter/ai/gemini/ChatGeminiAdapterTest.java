package com.elias.mismonedas.chat.adapter.ai.gemini;

import com.elias.mismonedas.chat.domain.enums.ChatSkill;
import com.elias.mismonedas.chat.dto.ai.ChatMessageRequest;
import com.elias.mismonedas.chat.dto.ai.PromptRequest;
import com.elias.mismonedas.chat.exception.ChatUnavailableException;
import com.elias.mismonedas.shared.adapter.ai.gemini.InteractionClient;
import com.elias.mismonedas.shared.adapter.ai.gemini.dto.InteractionRequest;
import com.elias.mismonedas.shared.adapter.ai.gemini.exception.GeminiException;
import com.elias.mismonedas.shared.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatGeminiAdapterTest {

    @Test
    void shouldFailExplicitlyWhenPrimaryModelIsUnavailable() {
        InteractionClient client = mock(InteractionClient.class);
        when(client.create(any(InteractionRequest.class)))
                .thenThrow(new GeminiException.UnavailableException("provider unavailable"));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            ChatGeminiAdapter adapter = new ChatGeminiAdapter(
                    new AiProperties(500, 256, 0.3f, 0.7f, 2_000), client, executor);
            ReflectionTestUtils.setField(adapter, "primaryModel", "gemini-primary");

            assertThatThrownBy(() -> adapter.sendMessage(request()))
                    .isInstanceOf(ChatUnavailableException.class)
                    .hasMessage("Chat service is temporarily unavailable");

            verify(client).create(any(InteractionRequest.class));
        }
    }

    private ChatMessageRequest request() {
        return ChatMessageRequest.builder()
                .sessionId(1L)
                .promptRequest(new PromptRequest("Tengo una consulta de presupuesto", null))
                .skill(ChatSkill.CHITCHAT)
                .build();
    }
}
