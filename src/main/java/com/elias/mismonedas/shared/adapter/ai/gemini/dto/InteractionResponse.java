package com.elias.mismonedas.shared.adapter.ai.gemini.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;


















@JsonIgnoreProperties(ignoreUnknown = true)
public record InteractionResponse(

        @JsonProperty("id")
        String id,

        @JsonProperty("status")
        String status,

        @JsonProperty("model")
        String model,

        @JsonProperty("steps")
        List<Step> steps,

        @JsonProperty("usage")
        Usage usage

) {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = Step.UnknownStep.class)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Step.ModelOutputStep.class, name = "model_output"),
            @JsonSubTypes.Type(value = Step.ThoughtStep.class,     name = "thought"),
            @JsonSubTypes.Type(value = Step.FunctionCallStep.class, name = "function_call")
    })
    @JsonIgnoreProperties(ignoreUnknown = true)
    public sealed interface Step permits
            Step.ModelOutputStep, Step.ThoughtStep,
            Step.FunctionCallStep, Step.UnknownStep {

        default String extractText() { return null; }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record ModelOutputStep(
                @JsonProperty("content") List<Content> content
        ) implements Step {
            @Override
            public String extractText() {
                if (content == null) return null;
                return content.stream()
                        .filter(c -> "text".equals(c.type()))
                        .map(Content::text)
                        .findFirst()
                        .orElse(null);
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record ThoughtStep(
                @JsonProperty("content") List<Content> content
        ) implements Step {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record FunctionCallStep(
                @JsonProperty("name")  String name,
                @JsonProperty("input") Object input
        ) implements Step {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record UnknownStep() implements Step {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(
            @JsonProperty("type") String type,
            @JsonProperty("text") String text
    ) {}




    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("total_input_tokens")  Integer totalInputTokens,
            @JsonProperty("total_output_tokens") Integer totalOutputTokens,
            @JsonProperty("total_thought_tokens") Integer totalThoughtTokens,
            @JsonProperty("total_cached_tokens") Integer totalCachedTokens,
            @JsonProperty("total_tokens")        Integer totalTokens
    ) {}




    public String extractOutputText() {
        if (steps == null) return null;
        return steps.stream()
                .filter(s -> s instanceof Step.ModelOutputStep)
                .map(Step::extractText)
                .filter(t -> t != null && !t.isBlank())
                .findFirst()
                .orElse(null);
    }
}