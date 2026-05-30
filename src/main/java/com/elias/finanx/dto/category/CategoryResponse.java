package com.elias.finanx.dto.category;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CategoryResponse {
    private Long id;
    private String name;
    private String description;
    private Boolean active;
    private LocalDateTime disabledAt;
}
