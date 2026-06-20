package com.elias.finanx.dto.ai;

import lombok.Data;

@Data
public class PromptRequest {
    String userPrompt;
    String context;
}
