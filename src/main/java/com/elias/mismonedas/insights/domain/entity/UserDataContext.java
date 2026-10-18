package com.elias.mismonedas.insights.domain.entity;

import com.elias.mismonedas.identity.domain.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Resumen analitico precalculado de un usuario (mes actual y mes anterior), utilizado por el
 * chat como contexto financiero.
 *
 * <p>F3: esta entidad vivia en {@code identity} y obligaba a {@code identity} a depender de
 * {@code insights} (el inverso de lo correcto: {code identity} es el modulo raiz). Al estar
 * aqui, la inversion se invierte: {@code chat} y {@code ledger} dependen de {@code insights},
 * nunca al reves.
 */
@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "contexto_datos_usuario")
public class UserDataContext {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actual", columnDefinition = "TEXT")
    private String currentData;

    @Column(name = "anterior", columnDefinition = "TEXT")
    private String previousData;

    @Column(name = "fecha_creacion")
    private LocalDate lastDate;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario")
    private User user;

    @PrePersist
    private void onCreate() {
        lastDate = LocalDate.now();
    }

    @PreUpdate
    private void onUpdate() {
        lastDate = LocalDate.now();
    }
}
