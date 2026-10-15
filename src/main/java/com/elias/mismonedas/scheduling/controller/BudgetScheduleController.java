package com.elias.mismonedas.scheduling.controller;

import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.scheduling.domain.enums.ScheduleState;
import com.elias.mismonedas.scheduling.dto.schedule.BudgetScheduleResponse;
import com.elias.mismonedas.scheduling.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/schedule/budgets")
@RequiredArgsConstructor
public class BudgetScheduleController {

    private final ScheduleService scheduleService;
    private final SecurityService securityService;

    @Operation(
            summary = "Listar programaciones de presupuestos",
            description = "Obtiene programaciones de presupuestos del usuario autenticado por estado.",
            operationId = "findAllBudgetScheduleByUserAndState",
            tags = {"Schedule-Budgets"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido")
            }
    )
    @GetMapping("/me/{state}")
    public ResponseEntity<List<BudgetScheduleResponse>> findAllByUserAndState(
            @Parameter(description = "Estado") @PathVariable ScheduleState state) {
        return ResponseEntity.ok(
                scheduleService.findAllBScheduleActiveByUserAndState(securityService.getCurrentUser().getId(), state)
        );
    }
}
