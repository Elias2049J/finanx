package com.elias.mismonedas.controller;

import com.elias.mismonedas.dto.schedule.BudgetScheduleResponse;
import com.elias.mismonedas.entity.enums.ScheduleState;
import com.elias.mismonedas.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/schedule/budgets")
@RequiredArgsConstructor
public class BudgetScheduleController {

    private final ScheduleService scheduleService;

    @Operation(
            summary = "Listar programaciones de presupuestos",
            description = "Obtiene programaciones de presupuestos por usuario y estado.",
            operationId = "findAllBudgetScheduleByUserAndState",
            tags = {"Schedule-Budgets"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido")
            }
    )
    @GetMapping("/user/{idUser}/{state}")
    public ResponseEntity<List<BudgetScheduleResponse>> findAllByUserAndState(
            @Parameter(description = "ID usuario") @PathVariable Long idUser,
            @Parameter(description = "Estado") @PathVariable ScheduleState state) {
        return ResponseEntity.ok(
                scheduleService.findAllBScheduleActiveByUserAndState(idUser, state)
        );
    }
}