package com.elias.finanx.entity;

import com.elias.finanx.entity.enums.Role;
import com.elias.finanx.entity.enums.TimeZone;
import com.elias.finanx.entity.enums.UserState;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

@Entity
@Table(
    name = "usuarios",
    indexes = {
        @Index(name = "idx_usuario_nombre_usuario", columnList = "nombre_usuario"),
        @Index(name = "idx_usuario_email", columnList = "email")
    }
)
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombres")
    private String name;

    @Column(name = "apellidos")
    private String lastname;

    @Column(name = "balance")
    private BigDecimal moneyBalance;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "nombre_usuario", unique = true, nullable = false)
    private String username;

    @Column(name = "clave", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol")
    private Role role;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private UserPreferences preferences;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    private UserState state;

    @Column(name = "fecha_creacion")
    private OffsetDateTime createdAt;

    @Column(name = "fecha_desactivacion")
    private OffsetDateTime disabledAt;

    public void disable() {
        this.state = UserState.DISABLED;
    }

    public void block() {
        this.state = UserState.BLOCKED;
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null)
            createdAt = OffsetDateTime.now(ZoneId.of("America/Lima"));
        if (state == null)
            state = UserState.ENABLED;
        if (moneyBalance == null)
            moneyBalance = BigDecimal.ZERO;
        if (role == null)
            role = Role.USER;
    }

    @Transient
    public TimeZone getTimeZone() {
        return preferences != null ? preferences.getTimeZone() : TimeZone.AMERICA_LIMA;
    }

    @Transient
    public List<FcmToken> getFcmTokens() {
        return preferences != null ? preferences.getFcmTokens() : List.of();
    }

    @Override
    public boolean isEnabled() {
        return state.isEnabled();
    }

    public String getFullName() {
        return (name == null ? "" : name).concat(" ").concat(lastname == null ? "" : lastname).trim().toUpperCase();
    }
}