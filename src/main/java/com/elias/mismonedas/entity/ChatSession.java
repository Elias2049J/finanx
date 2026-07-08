package com.elias.mismonedas.entity;

import com.elias.mismonedas.entity.enums.ChatSessionState;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sesion de chat del usuario.
 *
 * <p>Campos clave para la Interactions API:
 * <ul>
 *   <li>{@code interactionId} — ID retornado por Google tras cada turno.
 *       Se pasa como {@code previous_interaction_id} en el siguiente turno
 *       para que Google recupere el contexto sin reenviar el historial completo.
 *       Es {@code null} antes del primer turno. Google lo retiene 1 dia (free tier).</li>
 *   <li>{@code financialContext} — resumen financiero serializado al inicio de
 *       sesion; se inyecta en el system instruction solo cuando skill = ADVISOR.</li>
 * </ul>
 */
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Builder
@Entity
@Table(name = "sesion_chat")
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_sesion")
    private String uuid;

    @Column(name = "fecha_creacion")
    private OffsetDateTime createdAt;

    @Column(name = "fecha_fin")
    private OffsetDateTime endedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    private ChatSessionState state;

    /**
     * ID de la última interaccion almacenada en Gemini server-side.
     * Formato: "v1_Chd..." — retornado en cada {@code InteractionResponse.id()}.
     * Null hasta que se completa el primer turno.
     */
    @Column(name = "id_interaccion")
    private String interactionId;

    /**
     * Contexto financiero serializado al inicio de la sesion.
     * Contiene el resumen del periodo actual y el anterior,
     * formateado para inyeccion directa en el system instruction de Gemini.
     * Solo se inyecta al adapter cuando la skill detectada es ADVISOR.
     */
    @Column(name = "contexto_financiero", columnDefinition = "TEXT")
    private String financialContext;

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private User user;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();

    @PrePersist
    private void onCreate() {
        uuid = UUID.randomUUID().toString();
        createdAt = OffsetDateTime.now(user.getTimeZone().toZoneId());
        if (state == null) {
            state = ChatSessionState.ACTIVE;
        }
    }

    public void closeSession() {
        endedAt = OffsetDateTime.now(user.getTimeZone().toZoneId());
        state = ChatSessionState.CLOSED;
    }
}