package com.elias.mismonedas.notifications.dto.notification;
import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.planning.domain.enums.BudgetState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Objeto de transferencia que representa una notificacion generada en el sistema")
public class NotificationDTO {

    @Schema(description = "Identificador unico de la notificacion", example = "1001")
    private Long id;

    @Schema(description = "Identificador del usuario asociado a la notificacion", example = "42")
    private Long userId;

    @Schema(description = "Correo electronico del usuario", example = "usuario@ejemplo.com")
    private String userEmail;

    @Schema(description = "Locale utilizado para renderizar esta notificacion", example = "en-US")
    private String locale;

    @Schema(description = "Titulo localizado de la notificacion", example = "Mis Monedas - Notice")
    private String title;

    @Schema(description = "Indica si el usuario tiene habilitadas las notificaciones por email", example = "true")
    private boolean emailNotifications;

    @Schema(description = "Tipo de notificacion", implementation = NotificationType.class, example = "ALERTA_PRESUPUESTO")
    private NotificationType type;

    @Schema(description = "Mensaje de la notificacion", example = "Has alcanzado el 80% de tu presupuesto de alimentacion")
    private String message;

    @Schema(description = "Version HTML del mensaje para el envio por correo")
    private String htmlMessage;

    @Schema(description = "Fecha y hora de creacion de la notificacion", example = "2026-06-25T23:59:59")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha y hora en que la notificacion fue deshabilitada, si aplica", example = "2026-07-01T12:00:00")
    private LocalDateTime disabledAt;

    @Schema(description = "Fecha y hora programada para enviar la notificacion", example = "2026-06-30T08:00:00")
    private LocalDateTime scheduledAt;

    @Schema(description = "Fecha y hora en que la notificacion fue enviada", example = "2026-06-30T08:01:00")
    private LocalDateTime sentAt;

    @Schema(description = "Estado actual de la notificacion", implementation = NotificationState.class, example = "SENT")
    private NotificationState state;

    @Schema(description = "Identificador de la programacion asociada", example = "200")
    private Long scheduleId;

    @Schema(description = "Identificador del presupuesto asociado", example = "300")
    private Long budgetId;

    @Schema(description = "Descripcion del presupuesto asociado", example = "Presupuesto de transporte")
    private String budgetDescription;

    @Schema(description = "Estado del presupuesto asociado", implementation = BudgetState.class, example = "ACTIVE")
    private BudgetState budgetState;

    @Schema(description = "Identificador de la meta de ahorro asociada", example = "400")
    private Long savingGoalId;

    @Schema(description = "Descripcion de la meta de ahorro asociada", example = "Ahorro para viaje")
    private String savingGoalDescription;
}
