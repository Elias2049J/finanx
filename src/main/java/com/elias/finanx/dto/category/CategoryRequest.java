package com.elias.finanx.dto.category;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class CategoryRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String description;
    @NotNull
    private Long userId;
}
