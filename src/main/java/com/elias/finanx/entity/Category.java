package com.elias.finanx.entity;

import com.elias.finanx.entity.enums.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "categorias",
        indexes = {
                @Index(name = "idx_categoria_descripcion", columnList = "descripcion")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 120)
    private String name;

    @Column(name = "descripcion")
    private String description;

    @Column(name = "activo", nullable = false)
    private Boolean active = true;

    @Column(name = "fecha_creacion")
    private OffsetDateTime createdAt;

    @Column(name = "fecha_desactivacion")
    private OffsetDateTime disabledAt;

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private User user;

    @PrePersist
    private void onCreate(){
        if (active == null)  {
            active = true;
        }
    }
}
