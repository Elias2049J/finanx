package com.elias.finanx.controller;

import com.elias.finanx.dto.schedule.BudgetScheduleResponse;
import com.elias.finanx.dto.schedule.ScheduleRequest;
import com.elias.finanx.dto.schedule.ScheduleResponse;
import com.elias.finanx.entity.enums.ScheduleState;
import com.elias.finanx.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping
    public ResponseEntity<ScheduleResponse> create(@Valid @RequestBody ScheduleRequest request) {
        ScheduleResponse saved = scheduleService.create(request);
        return ResponseEntity.created(URI.create("/schedule/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ScheduleResponse> update(@PathVariable Long id, @Valid @RequestBody ScheduleRequest request) {
        return ResponseEntity.ok(scheduleService.update(id, request));
    }

    @GetMapping("/{idUser}/{state}")
    public ResponseEntity<List<ScheduleResponse>> findAllByUserAndState(
            @PathVariable Long idUser,
            @PathVariable ScheduleState state
    ) {
        return ResponseEntity.ok(scheduleService.findAllActiveByUserAndState(idUser, state));
    }

    @PostMapping("/{id}")
    public ResponseEntity<Void> pause(@PathVariable Long id) {
        scheduleService.pause(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}")
    public ResponseEntity<Void> resume(@PathVariable Long id) {
        scheduleService.resume(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        scheduleService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        scheduleService.disable(id);
        return ResponseEntity.noContent().build();
    }
}

