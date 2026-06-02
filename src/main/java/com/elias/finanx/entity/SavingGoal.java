package com.elias.finanx.entity;

import com.elias.finanx.entity.enums.SavingGoalState;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static java.math.RoundingMode.HALF_UP;

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

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "fecha_limite")
    private OffsetDateTime deadline;

    @Column(name = "fecha_creacion")
    private OffsetDateTime createdAt;

    @Column(name = "fecha_desactivacion")
    private OffsetDateTime disabledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    private SavingGoalState state;

    @Column(name = "activo")
    private Boolean active;

    @ManyToOne
    @JoinColumn(name = "id_usuario")
    private User user;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "savingGoal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Transaction> transactions = new ArrayList<>();

    @PrePersist
    private void onCreate() {
        active = true;
        state = SavingGoalState.RUNNING;
        createdAt = OffsetDateTime.now(user.getTimeZone().toZoneId());
    }

    public double getProgressPercentage() {
        if (targetAmount == null || targetAmount.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return getAccumulated().divide(targetAmount, 2, HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    public BigDecimal getAccumulated() {
        return transactions
                .stream()
                .filter(t -> Boolean.TRUE.equals(t.getActive()))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

    }

    public BigDecimal getOutstanding() {
        return targetAmount.subtract(getAccumulated());

    }

    public int getTransactionsCount() {
        return transactions.stream()
                .mapToInt(t -> Boolean.TRUE.equals(t.getActive()) ? 1 : 0)
                .sum();
    }

    public long getDaysRemaining() {
        return Duration
                .between(OffsetDateTime.now(user.getTimeZone().toZoneId()), deadline)
                .toDays();
    }

    public BigDecimal getAverageContribution() {
        long count = getTransactionsCount();
        return count == 0 ? BigDecimal.ZERO :
                getAccumulated().divide(BigDecimal.valueOf(count), 2, HALF_UP);
    }

    public boolean isCompleted() {
        return getAccumulated().compareTo(targetAmount) >= 0;
    }

    public ZonedDateTime getEstimatedCompletionDate() {
        BigDecimal outstanding = getOutstanding();
        BigDecimal avg = getAverageContribution();
        if (avg.compareTo(BigDecimal.ZERO) == 0) return null;
        long months = outstanding.divide(avg, 0, HALF_UP).longValue();
        return ZonedDateTime.now(user.getTimeZone().toZoneId()).plusMonths(months);
    }
}
