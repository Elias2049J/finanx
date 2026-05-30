package com.elias.finanx.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
@Getter
@Setter
@AllArgsConstructor
public class ErrorResponse {
    private String method;
    private String path;
    private String message;
    private int status;
    private String timestamp;
}