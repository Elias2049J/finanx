package com.elias.finanx.controller;

import com.elias.finanx.dto.notification.NotificationDTO;
import com.elias.finanx.entity.enums.NotificationState;
import com.elias.finanx.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/user/{idUser}")
    public ResponseEntity<List<NotificationDTO>> findAllByUser(@PathVariable Long idUser) {
        return ResponseEntity.ok(notificationService.findAllByUser(idUser));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.findById(id));
    }

    @GetMapping("/user/{idUser}/{state}")
    public ResponseEntity<List<NotificationDTO>> findAllByUserAndState(
            @PathVariable Long idUser,
            @PathVariable NotificationState state) {
        return ResponseEntity.ok(notificationService.findAllByUserAndState(idUser, state));
    }

    @PostMapping("/discard")
    public ResponseEntity<Void> discard(@RequestBody Long idNotification) {
        notificationService.discard(idNotification);
        return ResponseEntity.noContent().build();
    }
}
