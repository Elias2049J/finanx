package com.elias.finanx.dto.report;

import com.elias.finanx.dto.user.UserResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
public class BalanceReportResponse extends Report {
    private UserResponseDTO user;
    private BigDecimal balance;
    private BigDecimal totalSpent;
    private BigDecimal totalIncome;

    private Long totalTransactionCountToday;
}
