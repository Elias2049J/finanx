package com.elias.finanx.controller;

import com.elias.finanx.dto.schedule.ScheduleResponse;
import com.elias.finanx.dto.schedule.TransactionScheduleResponse;
import com.elias.finanx.entity.enums.ScheduleState;
import com.elias.finanx.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/schedule/transactions")
@RequiredArgsConstructor
public class TransactionScheduleController {

    private final ScheduleService scheduleService;

    @Operation(
            summary = "Listar programaciones de transacciones",
            description = "Obtiene programaciones de transacciones por usuario y estado.",
            operationId = "transactionScheduleFindByUserAndState",
            tags = {"Schedule-Transactions"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido")
            }
    )
    @GetMapping("/user/{idUser}/{state}")
    public ResponseEntity<List<TransactionScheduleResponse>> findAllByUserAndState(
            @Parameter(description = "ID usuario") @PathVariable Long idUser,
            @Parameter(description = "Estado") @PathVariable ScheduleState state) {
        return ResponseEntity.ok(
                scheduleService.findAllTScheduleActiveByUserAndState(idUser, state)
        );
    }
}