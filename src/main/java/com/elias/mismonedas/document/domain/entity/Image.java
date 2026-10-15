package com.elias.mismonedas.document.domain.entity;

import com.elias.mismonedas.identity.domain.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
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

    @Column(name = "bucket_name", nullable = false, length = 255)
    private String bucketName;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

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
