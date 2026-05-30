package com.elias.finanx.entity.enums;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@Getter
@AllArgsConstructor
public enum FileType {
    CSV("CSV"),
    PDF("PDF"),
    EXCEL("Excel");

    private final String displayName;
}
