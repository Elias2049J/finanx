package com.elias.mismonedas.ledger.repository;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.domain.entity.Reason;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.catalog.repository.ReasonRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.repository.projection.AnalyticsDailyAggregate;
import com.elias.mismonedas.ledger.repository.projection.AnalyticsDimensionAggregate;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.wallets.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.messaging.rabbitmq.consumer-auto-startup=false",
        "spring.task.scheduling.enabled=false"
})
@Transactional
class TransactionAnalyticsAggregationTest {

    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-05-01T00:00:00-05:00");
    private static final OffsetDateTime TO = OffsetDateTime.parse("2026-07-01T00:00:00-05:00");
    private static final String LIMA = "America/Lima";

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ReasonRepository reasonRepository;

    private User user;
    private Wallet pen;
    private Wallet usd;
    private Category spentCategory;
    private Category incomeCategory;
    private Reason reason;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setName("Analytics");
        user.setLastname("Aggregation");
        user.setEmail("analytics-aggregation-" + UUID.randomUUID() + "@example.test");
        user.setPassword("irrelevant-password");
        user = userRepository.saveAndFlush(user);

        pen = walletRepository.saveAndFlush(Wallet.builder()
                .user(user)
                .currencyCode("PEN")
                .balance(BigDecimal.ZERO)
                .primary(true)
                .build());
        usd = walletRepository.saveAndFlush(Wallet.builder()
                .user(user)
                .currencyCode("USD")
                .balance(BigDecimal.ZERO)
                .primary(false)
                .build());

        spentCategory = categoryRepository.saveAndFlush(category("Comida", TransactionType.SPENT));
        incomeCategory = categoryRepository.saveAndFlush(category("Sueldo", TransactionType.INCOME));

        reason = new Reason();
        reason.setDescription("Almuerzo");
        reason.setUser(user);
        reason = reasonRepository.saveAndFlush(reason);
    }

    @Test
    void shouldGroupDailyByUserTimeZoneInsteadOfTransaction() {
        saveTransaction("10.00", TransactionType.SPENT, pen, spentCategory,
                OffsetDateTime.parse("2026-06-01T03:00:00Z"));
        saveTransaction("20.00", TransactionType.SPENT, pen, spentCategory,
                OffsetDateTime.parse("2026-06-01T15:00:00Z"));
        saveTransaction("30.00", TransactionType.SPENT, pen, spentCategory,
                OffsetDateTime.parse("2026-06-01T18:00:00Z"));
        saveTransaction("5.00", TransactionType.INCOME, usd, incomeCategory,
                OffsetDateTime.parse("2026-06-01T16:00:00Z"));

        List<AnalyticsDailyAggregate> rows = transactionRepository.aggregateDaily(
                user.getId(), null, FROM, TO, LIMA);

        assertThat(rows).hasSize(3);

        AnalyticsDailyAggregate lateNight = findDaily(rows, LocalDate.of(2026, 5, 31), TransactionType.SPENT);
        assertThat(lateNight.getCurrencyCode()).isEqualTo("PEN");
        assertThat(lateNight.getTotal()).isEqualByComparingTo("10.00");
        assertThat(lateNight.getOccurrences()).isEqualTo(1L);

        AnalyticsDailyAggregate juneSpent = findDaily(rows, LocalDate.of(2026, 6, 1), TransactionType.SPENT);
        assertThat(juneSpent.getCurrencyCode()).isEqualTo("PEN");
        assertThat(juneSpent.getTotal()).isEqualByComparingTo("50.00");
        assertThat(juneSpent.getOccurrences()).isEqualTo(2L);

        AnalyticsDailyAggregate juneIncome = findDaily(rows, LocalDate.of(2026, 6, 1), TransactionType.INCOME);
        assertThat(juneIncome.getCurrencyCode()).isEqualTo("USD");
        assertThat(juneIncome.getTotal()).isEqualByComparingTo("5.00");
        assertThat(juneIncome.getOccurrences()).isEqualTo(1L);
    }

    @Test
    void shouldGroupByDimensionsAndReturnOneRowPerCombination() {
        saveTransaction("10.00", TransactionType.SPENT, pen, spentCategory,
                OffsetDateTime.parse("2026-06-01T15:00:00Z"));
        saveTransaction("20.00", TransactionType.SPENT, pen, spentCategory,
                OffsetDateTime.parse("2026-06-02T15:00:00Z"));
        saveTransaction("7.00", TransactionType.SPENT, usd, spentCategory,
                OffsetDateTime.parse("2026-06-01T16:00:00Z"));
        saveTransaction("5.00", TransactionType.INCOME, pen, incomeCategory,
                OffsetDateTime.parse("2026-06-01T17:00:00Z"));

        List<AnalyticsDimensionAggregate> rows = transactionRepository.aggregateByDimensions(
                user.getId(), null, FROM, TO);

        assertThat(rows).hasSize(3);

        AnalyticsDimensionAggregate spentPen = findDimension(rows, TransactionType.SPENT, "PEN");
        assertThat(spentPen.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(spentPen.getCategoryId()).isEqualTo(spentCategory.getId());
        assertThat(spentPen.getCategoryName()).isEqualTo("Comida");
        assertThat(spentPen.getReasonId()).isEqualTo(reason.getId());
        assertThat(spentPen.getReasonDescription()).isEqualTo("Almuerzo");
        assertThat(spentPen.getTotal()).isEqualByComparingTo("30.00");
        assertThat(spentPen.getOccurrences()).isEqualTo(2L);
        assertThat(spentPen.getMinAmount()).isEqualByComparingTo("10.00");
        assertThat(spentPen.getMaxAmount()).isEqualByComparingTo("20.00");

        AnalyticsDimensionAggregate spentUsd = findDimension(rows, TransactionType.SPENT, "USD");
        assertThat(spentUsd.getTotal()).isEqualByComparingTo("7.00");
        assertThat(spentUsd.getOccurrences()).isEqualTo(1L);

        AnalyticsDimensionAggregate incomePen = findDimension(rows, TransactionType.INCOME, "PEN");
        assertThat(incomePen.getTotal()).isEqualByComparingTo("5.00");
        assertThat(incomePen.getOccurrences()).isEqualTo(1L);
    }

    private Category category(String name, TransactionType type) {
        Category category = new Category();
        category.setName(name);
        category.setType(type);
        category.setUser(user);
        return category;
    }

    private void saveTransaction(String amount,
                                 TransactionType type,
                                 Wallet wallet,
                                 Category category,
                                 OffsetDateTime createdAt) {
        Transaction transaction = new Transaction();
        transaction.setAmount(new BigDecimal(amount));
        transaction.setType(type);
        transaction.setPaymentMethod(PaymentMethod.CASH);
        transaction.setWallet(wallet);
        transaction.setUser(user);
        transaction.setCategory(category);
        transaction.setReason(reason);
        transaction.setCreatedAt(createdAt);
        transaction.setActive(true);
        transactionRepository.saveAndFlush(transaction);
    }

    private AnalyticsDailyAggregate findDaily(List<AnalyticsDailyAggregate> rows,
                                              LocalDate day,
                                              TransactionType type) {
        return rows.stream()
                .filter(row -> day.equals(row.getDay()) && type == row.getType())
                .findFirst()
                .orElseThrow();
    }

    private AnalyticsDimensionAggregate findDimension(List<AnalyticsDimensionAggregate> rows,
                                                      TransactionType type,
                                                      String currencyCode) {
        return rows.stream()
                .filter(row -> type == row.getType() && currencyCode.equals(row.getCurrencyCode()))
                .findFirst()
                .orElseThrow();
    }
}
