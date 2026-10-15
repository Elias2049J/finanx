package com.elias.mismonedas.document.outbox;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "receipt_scan_outbox")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptScanOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_comprobante", nullable = false)
    private Long receiptId;

    @Column(nullable = false)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private ReceiptScanOutboxState state;

    @Column(name = "fecha_creacion", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "fecha_publicacion")
    private OffsetDateTime publishedAt;
}
