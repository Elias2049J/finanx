package com.elias.finanx.controller;

import com.elias.finanx.dto.schedule.BudgetScheduleResponse;
import com.elias.finanx.dto.schedule.ScheduleResponse;
import com.elias.finanx.entity.enums.ScheduleState;
import com.elias.finanx.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/schedule/budgets")
@RequiredArgsConstructor
public class BudgetScheduleController {
    private final ScheduleService scheduleService;

    @GetMapping("/{idUser}/{state}")
    public ResponseEntity<List<BudgetScheduleResponse>> findAllByUserAndState(
            @PathVariable Long idUser,
            @PathVariable ScheduleState state
            ) {
        return ResponseEntity.ok(scheduleService.findAllBScheduleActiveByUserAndState(idUser, state));
    }
}
