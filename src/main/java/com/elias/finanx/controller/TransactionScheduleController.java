package com.elias.finanx.controller;

import com.elias.finanx.dto.scheduledtransaction.TransactionScheduleRequest;
import com.elias.finanx.dto.scheduledtransaction.TransactionScheduleResponse;
import com.elias.finanx.service.TransactionScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class TransactionScheduleController {

    private final TransactionScheduleService tsService;

    @PostMapping
    public ResponseEntity<TransactionScheduleResponse> create(@Valid @RequestBody TransactionScheduleRequest request) {
        TransactionScheduleResponse saved = tsService.create(request);
        return ResponseEntity.created(URI.create("/scheduled-transactions/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionScheduleResponse> update(@PathVariable Long id, @Valid @RequestBody TransactionScheduleRequest request) {
        return ResponseEntity.ok(tsService.update(id, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionScheduleResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(tsService.findById(id));
    }
}

