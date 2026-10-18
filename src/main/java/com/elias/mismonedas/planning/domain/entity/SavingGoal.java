package com.elias.mismonedas.planning.domain.entity;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.planning.domain.enums.SavingGoalState;
import com.elias.mismonedas.planning.domain.vo.SavingGoalTotals;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static java.math.RoundingMode.HALF_UP;

/**
 * F6: la entidad ya NO mantiene la coleccion {@code transactions} ni computa sus propios
 * acumulados. Los totales agregados (SUM por moneda en la base, ver
 * {@code SavingGoalServiceImpl#totalsOf}) llegan como {@link SavingGoalTotals} — olvidarlos
 * no compila, y nadie puede caer en un barrido accidental del historial.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "metas_ahorro")
public class SavingGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "descripcion", nullable = false)
    private String description;

    @Column(name = "monto_objetivo", nullable = false, precision = 12, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "codigo_moneda", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "fecha_limite")
    private OffsetDateTime deadline;

    @Column(name = "monto_acumulado_congelado", precision = 19, scale = 2)
    private BigDecimal frozenAccumulated;

    @Column(name = "fecha_creacion")
    private OffsetDateTime createdAt;

    @Column(name = "fecha_desactivacion")
    private OffsetDateTime disabledAt;

    @Column(name = "fecha_completado")
    private OffsetDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    private SavingGoalState state;

    @Column(name = "activo")
    private Boolean active;

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private User user;

    @PrePersist
    private void onCreate() {
        active = true;
        state = SavingGoalState.RUNNING;
        createdAt = OffsetDateTime.now(user.getTimeZone().toZoneId());
    }

    public long getDaysRemaining() {
        return Math.max(0, Duration
                .between(OffsetDateTime.now(user.getTimeZone().toZoneId()), deadline)
                .toDays());
    }

    public boolean hasReachedTarget(BigDecimal accumulated) {
        return accumulated.compareTo(targetAmount) >= 0;
    }

    public BigDecimal getOutstanding(SavingGoalTotals totals) {
        return targetAmount.subtract(totals.accumulated());
    }

    public double getProgressPercentage(SavingGoalTotals totals) {
        if (targetAmount == null || targetAmount.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return totals.accumulated().divide(targetAmount, 2, HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    public BigDecimal getAverageContribution(SavingGoalTotals totals) {
        int count = totals.transactionsCount();
        return count == 0 ? BigDecimal.ZERO :
                totals.accumulated().divide(BigDecimal.valueOf(count), 2, HALF_UP);
    }

    public LocalDateTime getEstimatedCompletionDate(SavingGoalTotals totals) {
        BigDecimal outstanding = getOutstanding(totals);
        BigDecimal avg = getAverageContribution(totals);
        if (avg.compareTo(BigDecimal.ZERO) == 0) return null;
        long months = outstanding.divide(avg, 0, HALF_UP).longValue();
        return OffsetDateTime.now(user.getTimeZone().toZoneId()).toLocalDateTime().plusMonths(months);
    }

    public boolean isCompleted(SavingGoalTotals totals) {
        return state == SavingGoalState.RUNNING
                && hasReachedTarget(totals.accumulated());
    }
}
