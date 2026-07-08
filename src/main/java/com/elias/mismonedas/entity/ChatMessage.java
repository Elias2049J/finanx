package com.elias.mismonedas.entity;

import com.elias.mismonedas.entity.enums.ChatRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "mensaje_chat")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_turno")
    private Integer turnNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol")
    private ChatRole role;

    @Column(columnDefinition = "TEXT", name = "mensaje")
    private String message;

    @Column(name = "tokens_usados")
    private Integer tokensUsed;

    @Column(name = "modelo")
    private String model;

    @Column(name = "motivo_finalizacion")
    private String finishReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ratings_seguridad", columnDefinition = "jsonb")
    private Map<String, Object> safetyRatings;

    @Column(name = "latencia")
    private Long latencyMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadatos", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "fecha_creacion")
    private OffsetDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "id_sesion")
    private ChatSession session;
}
