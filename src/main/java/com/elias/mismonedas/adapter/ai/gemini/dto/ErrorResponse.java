package com.elias.mismonedas.adapter.ai.gemini.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Envelope de error retornado por la Gemini API.
 *
 * <pre>
 * {
 *   "error": {
 *     "code": 429,
 *     "message": "Resource has been exhausted...",
 *     "status": "RESOURCE_EXHAUSTED"
 *   }
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ErrorResponse(
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