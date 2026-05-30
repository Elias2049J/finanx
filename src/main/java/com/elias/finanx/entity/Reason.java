package com.elias.finanx.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "motivos_movimiento",
        indexes = {
                @Index(name = "idx_motivo_movimiento_descripcion", columnList = "descripcion")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Reason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "descripcion", nullable = false)
    private String description;

    @Column(name = "activo", nullable = false)
    private Boolean active;

    @ManyToOne
    private User user;

    @PrePersist
    private void onCreate(){
        if (active == null)  {
            active = true;
        }
    }
}
