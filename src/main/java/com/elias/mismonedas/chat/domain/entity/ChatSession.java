package com.elias.mismonedas.chat.domain.entity;

import com.elias.mismonedas.chat.domain.enums.ChatSessionState;
import com.elias.mismonedas.identity.domain.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;













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





    @Column(name = "id_interaccion")
    private String interactionId;






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