package com.elias.finanx.dto.category;

import lombok.Data;
import jakarta.validation.constraints.*;

@Data
public class CategoryRequest {
    @NotBlank
    private String name;
    private String description;
    private Long userId;
}
