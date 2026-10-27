package com.elias.mismonedas.identity.controller;

import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.identity.dto.auth.AuthResponse;
import com.elias.mismonedas.identity.dto.user.UserRequest;
import com.elias.mismonedas.identity.dto.user.UserResponse;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.identity.service.UserService;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SecurityService securityService;

    @Operation(summary = "Listar usuarios", description = "Lista paginada con búsqueda opcional por nombre, apellido o email.", operationId = "listUsers", tags = {"Users"})
    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> listUsers(
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = PaginationDefaults.DEFAULT_PAGE_SIZE, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(userService.list(search, pageable));
    }

    @Operation(summary = "Obtener el usuario autenticado", operationId = "findCurrentUser", tags = {"Users"})
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(userService.findCurrentUser());
    }

    @Operation(summary = "Buscar usuario por ID", operationId = "findUserById", tags = {"Users"})
    @PreAuthorize("hasRole('SYSTEM')")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }



    @Operation(summary = "Actualizar datos de usuario", operationId = "updateUser", tags = {"Users"})
    @PreAuthorize("hasRole('SYSTEM')")
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @Operation(summary = "Deshabilitar usuario", operationId = "disableUser", tags = {"Users"})
    @PreAuthorize("hasRole('SYSTEM')")
    @PostMapping("/{id}/disable")
    public ResponseEntity<UserResponse> disable(@PathVariable Long id) {
        return ResponseEntity.ok(userService.disable(id));
    }

    @Operation(summary = "Habilitar usuario", operationId = "enableUser", tags = {"Users"})
    @PreAuthorize("hasRole('SYSTEM')")
    @PostMapping("/{id}/activate")
    public ResponseEntity<UserResponse> enable(@PathVariable Long id) {
        return ResponseEntity.ok(userService.enable(id));
    }

    @Operation(summary = "Bloquear usuario", operationId = "blockUser", tags = {"Users"})
    @PreAuthorize("hasRole('SYSTEM')")
    @PostMapping("/{id}/block")
    public ResponseEntity<UserResponse> block(@PathVariable Long id) {
        return ResponseEntity.ok(userService.block(id));
    }

    @Operation(summary = "Listar zonas horarias disponibles", operationId = "listTimeZones", tags = {"Users"})
    @GetMapping("/timezones")
    public ResponseEntity<TimeZone[]> listTimeZones() {
        return ResponseEntity.ok(userService.listTimeZones());
    }

    @Operation(summary = "Solicitar eliminacion de cuenta", operationId = "requestAccountDeletion", tags = {"Users"})
    @PreAuthorize("hasRole('USER') or hasRole('SYSTEM')")
    @PostMapping("/{id}/request-deletion")
    public ResponseEntity<Void> requestDeletion(@PathVariable Long id) {
        authorizeDeletionTarget(id);
        userService.requestAccountDeletion(id);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Cancelar eliminacion de cuenta", operationId = "cancelAccountDeletion", tags = {"Users"})
    @PreAuthorize("hasRole('USER') or hasRole('SYSTEM')")
    @PostMapping("/{id}/cancel-deletion")
    public ResponseEntity<Void> cancelDeletion(@PathVariable Long id) {
        authorizeDeletionTarget(id);
        userService.cancelAccountDeletion(id);
        return ResponseEntity.noContent().build();
    }

    private void authorizeDeletionTarget(Long targetUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean systemUser = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SYSTEM".equals(authority.getAuthority()));
        if (systemUser) {
            return;
        }

        Long currentUserId = securityService.getCurrentUser().getId();
        if (!Objects.equals(currentUserId, targetUserId)) {
            throw new AccessDeniedException("Cannot manage another user's account deletion");
        }
    }

}
