package com.elias.mismonedas.scheduling.controller;

import com.elias.mismonedas.scheduling.domain.enums.ScheduleState;
import com.elias.mismonedas.scheduling.dto.schedule.TransactionScheduleResponse;
import com.elias.mismonedas.scheduling.service.ScheduleService;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schedule/transactions")
@RequiredArgsConstructor
public class TransactionScheduleController {

    private final ScheduleService scheduleService;

    @Operation(
            summary = "Listar programaciones de transacciones",
            description = "Lista programaciones de transacciones activas y paginadas con filtro opcional state.",
            operationId = "listTransactionSchedules",
            tags = {"Schedule-Transactions"},
            responses = {
                    @ApiResponse(responseCode = "200", description = "Listado obtenido")
            }
    )
    @GetMapping
    public ResponseEntity<PageResponse<TransactionScheduleResponse>> listTransactionSchedules(
            @RequestParam(required = false) ScheduleState state,
            @ParameterObject @PageableDefault(size = PaginationDefaults.DEFAULT_PAGE_SIZE, sort = "nextRunAt", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ResponseEntity.ok(scheduleService.listTransactionSchedules(state, pageable));
    }
}
