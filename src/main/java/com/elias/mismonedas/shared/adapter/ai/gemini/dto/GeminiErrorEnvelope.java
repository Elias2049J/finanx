package com.elias.mismonedas.shared.adapter.ai.gemini.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;













@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiErrorEnvelope(
        @JsonProperty("error") GeminiError error
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeminiError(
            @JsonProperty("code")    int    code,
            @JsonProperty("message") String message,
            @JsonProperty("status")  String status
    ) {}

    public int code()    { return error != null ? error.code()    : 0; }
    public String message() { return error != null ? error.message() : "Unknown error"; }
    public String status()  { return error != null ? error.status()  : "UNKNOWN"; }
}