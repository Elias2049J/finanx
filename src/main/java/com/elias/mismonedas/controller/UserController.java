package com.elias.mismonedas.controller;

import com.elias.mismonedas.dto.auth.LoginResponse;
import com.elias.mismonedas.dto.user.UserRequest;
import com.elias.mismonedas.dto.user.UserResponse;
import com.elias.mismonedas.entity.enums.TimeZone;
import com.elias.mismonedas.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Listar todos los usuarios", operationId = "findAllUser", tags = {"Users"})
    @GetMapping
    public ResponseEntity<List<UserResponse>> findAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    @Operation(summary = "Buscar usuario por ID", operationId = "findUserById", tags = {"Users"})
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @Operation(summary = "Registrar un nuevo usuario", operationId = "registerUser", tags = {"Users"})
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody UserRequest request) {
        LoginResponse saved = userService.register(request);
        return ResponseEntity.created(URI.create("/users/" + saved.getUser().getId())).body(saved);
    }

    @Operation(summary = "Actualizar datos de usuario", operationId = "updateUser", tags = {"Users"})
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @Operation(summary = "Deshabilitar usuario", operationId = "disableUser", tags = {"Users"})
    @PostMapping("/{id}/disable")
    public ResponseEntity<UserResponse> disable(@PathVariable Long id) {
        return ResponseEntity.ok(userService.disable(id));
    }

    @Operation(summary = "Habilitar usuario", operationId = "enableUser", tags = {"Users"})
    @PostMapping("/{id}/activate")
    public ResponseEntity<UserResponse> enable(@PathVariable Long id) {
        return ResponseEntity.ok(userService.enable(id));
    }

    @Operation(summary = "Bloquear usuario", operationId = "blockUser", tags = {"Users"})
    @PostMapping("/{id}/block")
    public ResponseEntity<UserResponse> block(@PathVariable Long id) {
        return ResponseEntity.ok(userService.block(id));
    }

    @Operation(summary = "Buscar usuarios por nombre", operationId = "searchUserByName", tags = {"Users"})
    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> searchByName(@RequestParam("string") String q) {
        return ResponseEntity.ok(userService.searchByName(q));
    }

    @Operation(summary = "Listar zonas horarias disponibles", operationId = "listTimeZones", tags = {"Users"})
    @GetMapping("/timezones")
    public ResponseEntity<TimeZone[]> listTimeZones() {
        return ResponseEntity.ok(userService.listTimeZones());
    }

    @Operation(summary = "Eliminar usuario", operationId = "deleteUser", tags = {"Users"})
    @DeleteMapping("/{id}/delete")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.deleteUserAccount(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Resetear datos de usuario", operationId = "resetUserData", tags = {"Users"})
    @PostMapping("/{id}/reset-data")
    public ResponseEntity<Void> resetData(@PathVariable Long id) {
        userService.resetAccountData(id);
        return ResponseEntity.noContent().build();
    }
}
