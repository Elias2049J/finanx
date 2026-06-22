package com.elias.finanx.dto.category;

import com.elias.finanx.entity.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    private Long id;
    private String name;
    private String description;
    private TransactionType type;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime disabledAt;
}
