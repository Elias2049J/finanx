package com.elias.finanx.dto.category;

import com.elias.finanx.entity.enums.TransactionType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CategoryResponse {
    private Long id;
    private String name;
    private String description;
    private TransactionType type;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime disabledAt;
}
