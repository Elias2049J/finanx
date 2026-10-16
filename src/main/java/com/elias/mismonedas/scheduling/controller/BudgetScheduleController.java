package com.elias.mismonedas.scheduling.controller;

import com.elias.mismonedas.scheduling.domain.enums.ScheduleState;
import com.elias.mismonedas.scheduling.dto.schedule.BudgetScheduleResponse;
import com.elias.mismonedas.scheduling.service.ScheduleService;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedule/budgets")
@RequiredArgsConstructor
public class BudgetScheduleController {

    private final ScheduleService scheduleService;

    @Operation(
            summary = "Listar programaciones de presupuestos",
            description = "Lista programaciones de presupuestos activas y paginadas con filtro opcional state.",
            operationId = "listBudgetSchedules",
            tags = {"Schedule-Budgets"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido")
            }
    )
    @GetMapping
    public ResponseEntity<PageResponse<BudgetScheduleResponse>> listBudgetSchedules(
            @RequestParam(required = false) ScheduleState state,
            @ParameterObject @PageableDefault(size = PaginationDefaults.DEFAULT_PAGE_SIZE, sort = "nextRunAt", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(scheduleService.listBudgetSchedules(state, pageable));
    }
}
