package com.elias.mismonedas.planning.service;

import com.elias.mismonedas.planning.domain.enums.BudgetHealth;
import com.elias.mismonedas.planning.domain.enums.BudgetState;
import com.elias.mismonedas.planning.dto.budget.BudgetRequest;
import com.elias.mismonedas.planning.dto.budget.BudgetResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface BudgetService {
    BudgetResponse create(BudgetRequest request);
    BudgetResponse update(Long id, BudgetRequest request);
    BudgetResponse findById(Long id);
    List<BudgetResponse> findAllByUser(Long idUser);

    List<BudgetResponse> findAllActiveByUserAndState(Long idUser, BudgetState state);

    void cancel(Long id);
    void disable(Long id );

    void checkAllBudgets(Long userId);

    PageResponse<BudgetResponse> list(
            Boolean active,
            BudgetState state,
            BudgetHealth health,
            LocalDate from,
            LocalDate to,
            Pageable pageable);
}

