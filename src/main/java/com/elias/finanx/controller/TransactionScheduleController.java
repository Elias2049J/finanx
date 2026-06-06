package com.elias.finanx.controller;

import com.elias.finanx.dto.schedule.ScheduleResponse;
import com.elias.finanx.dto.schedule.TransactionScheduleResponse;
import com.elias.finanx.entity.enums.ScheduleState;
import com.elias.finanx.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/schedule/transactions")
@RequiredArgsConstructor
public class TransactionScheduleController {
    private final ScheduleService scheduleService;

    @GetMapping("/user/{idUser}/{state}")
    public ResponseEntity<List<TransactionScheduleResponse>> findAllByUserAndState(
            @PathVariable Long idUser,
            @PathVariable ScheduleState state
    ) {
        return ResponseEntity.ok(scheduleService.findAllTScheduleActiveByUserAndState(idUser, state));
    }
}
