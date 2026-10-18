package com.elias.mismonedas.shared.adapter.ai.gemini.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;






@JsonInclude(JsonInclude.Include.NON_NULL)
public record InteractionRequest(

        @JsonProperty("model")
        String model,

        @JsonProperty("input")
        String input,

        @JsonProperty("system_instruction")
        String systemInstruction,

        @JsonProperty("previous_interaction_id")
        String previousInteractionId,

        @JsonProperty("store")
        boolean store,

        @JsonProperty("generation_config")
        GenerationConfig generationConfig

) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GenerationConfig(
            @JsonProperty("temperature") Float temperature,
            @JsonProperty("max_output_tokens") Integer maxOutputTokens
    ) {}

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String model;
        private String input;
        private String systemInstruction;
        private String previousInteractionId;
        private boolean store = true;
        private GenerationConfig generationConfig;

        public Builder model(String model)                  { this.model = model; return this; }
        public Builder input(String input)                  { this.input = input; return this; }
        public Builder systemInstruction(String si)         { this.systemInstruction = si; return this; }
        public Builder previousInteractionId(String id)     { this.previousInteractionId = id; return this; }
        public Builder store(boolean store)                 { this.store = store; return this; }
        public Builder generationConfig(GenerationConfig c) { this.generationConfig = c; return this; }

        public InteractionRequest build() {
            return new InteractionRequest(
                    model, input, systemInstruction,
                    previousInteractionId, store,
                    generationConfig
            );
        }
    }
}