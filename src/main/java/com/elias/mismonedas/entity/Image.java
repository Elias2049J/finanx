package com.elias.mismonedas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "imagenes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private User user;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false, name = "tipo_contenido")
    private String mediaType;

    @Lob
    @Column(nullable = false)
    private byte[] data;

    @Column(nullable = false, name = "fecha_creacion")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            ZoneId zone = user != null ? user.getTimeZone().toZoneId() : ZoneId.systemDefault();
            createdAt = OffsetDateTime.now(zone);
        }
    }
}
