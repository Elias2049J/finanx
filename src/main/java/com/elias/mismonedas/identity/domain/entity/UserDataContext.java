package com.elias.mismonedas.identity.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

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
