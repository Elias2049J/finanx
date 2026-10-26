package com.elias.mismonedas.identity.domain.entity;

import com.elias.mismonedas.identity.domain.enums.AuthProvider;
import com.elias.mismonedas.identity.domain.enums.Role;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.identity.domain.enums.UserState;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(
    name = "usuarios",
    indexes = {
        @Index(name = "idx_usuario_email", columnList = "email")
    }
)
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

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "clave", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(name = "provider_user_id")
    private String providerUserId;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

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

    @Column(name = "solicitud_eliminacion_en")
    private OffsetDateTime deletionRequestedAt;

    @Column(name = "eliminacion_programada_en")
    private OffsetDateTime deletionScheduledAt;

    public void disable() {
        this.state = UserState.DISABLED;
    }

    public void block() {
        this.state = UserState.BLOCKED;
    }

    public void requestDeletion(OffsetDateTime requestedAt, OffsetDateTime scheduledAt) {
        this.deletionRequestedAt = requestedAt;
        this.deletionScheduledAt = scheduledAt;
        this.state = UserState.DISABLED;
    }

    public void cancelDeletion() {
        this.deletionRequestedAt = null;
        this.deletionScheduledAt = null;
        this.state = UserState.ENABLED;
    }

    public boolean isPendingDeletion() {
        return deletionRequestedAt != null && deletionScheduledAt != null;
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null) {
            return List.of();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null)
            createdAt = OffsetDateTime.now(getTimeZone().toZoneId());
        if (state == null)
            state = UserState.ENABLED;
        if (role == null)
            role = Role.USER;
        if (authProvider == null)
            authProvider = AuthProvider.LOCAL;
    }

    @Transient
    public TimeZone getTimeZone() {
        return preferences != null ? preferences.getTimeZone() : TimeZone.AMERICA_LIMA;
    }

    @Override
    public boolean isEnabled() {
        return state.isEnabled();
    }

    public String getFullName() {
        return (name == null ? "" : name).concat(" ").concat(lastname == null ? "" : lastname).trim().toUpperCase();
    }
}