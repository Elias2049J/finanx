package com.elias.finanx.dto.reason;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ReasonResponse {
    private Long id;

    private String description;

    private Long userId;
}
