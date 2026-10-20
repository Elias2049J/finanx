package com.elias.mismonedas.ledger.repository;

import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.wallets.repository.WalletRepository;
import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.domain.entity.Reason;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.catalog.repository.ReasonRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.messaging.rabbitmq.consumer-auto-startup=false")
@Transactional
class TransactionSpecificationsTest {

    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ReasonRepository reasonRepository;
    @Autowired private TransactionRepository transactionRepository;

    @Test
    void shouldScopeByUserAndApplyUserLocalInclusiveDateRangeWithPagination() {
        User owner = saveUser();
        User otherUser = saveUser();
        Wallet ownerWallet = saveWallet(owner);
        Wallet otherWallet = saveWallet(otherUser);
        Category ownerCategory = saveCategory(owner);
        Category otherCategory = saveCategory(otherUser);

        Transaction lastIncluded = saveTransaction(
                ownerWallet,
                ownerCategory,
                OffsetDateTime.parse("2026-06-30T23:59:59.999999-05:00"));
        saveTransaction(ownerWallet, ownerCategory, OffsetDateTime.parse("2026-07-01T00:00:00-05:00"));
        saveTransaction(otherWallet, otherCategory, OffsetDateTime.parse("2026-06-30T12:00:00-05:00"));

        ZoneId userZone = owner.getTimeZone().toZoneId();
        DateRange range = new DateRange(LocalDate.parse("2026-06-30"), LocalDate.parse("2026-06-30"));
        Page<Transaction> result = transactionRepository.findAll(
                TransactionSpecifications.list(
                        owner.getId(), true, null, null, null,
                        range.startInclusive(userZone), range.endExclusive(userZone), null),
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(Transaction::getId).containsExactly(lastIncluded.getId());
        assertThat(result.getSize()).isEqualTo(1);
    }

    @Test
    void shouldFilterBySearchAcrossReasonDescriptionAndCategoryName() {
        User owner = saveUser();
        Wallet wallet = saveWallet(owner);
        Category category = saveCategory(owner);
        Reason reason = saveReason(owner, "Compra de utiles escolares");

        Transaction withReason = saveTransaction(wallet, category, reason,
                OffsetDateTime.parse("2026-06-10T10:00:00-05:00"));
        Transaction withoutReason = saveTransaction(wallet, category, null,
                OffsetDateTime.parse("2026-06-11T10:00:00-05:00"));

        List<Long> byReason = searchIds(owner, "UTILES");
        assertThat(byReason).containsExactly(withReason.getId());

        List<Long> byCategory = searchIds(owner, "test category");
        assertThat(byCategory).containsExactlyInAnyOrder(withReason.getId(), withoutReason.getId());

        assertThat(searchIds(owner, "inexistente")).isEmpty();
    }

    private List<Long> searchIds(User owner, String term) {
        return transactionRepository.findAll(TransactionSpecifications.list(
                        owner.getId(), true, null, null, null, null, null, term))
                .stream()
                .map(Transaction::getId)
                .toList();
    }

    private Reason saveReason(User user, String description) {
        Reason reason = new Reason();
        reason.setUser(user);
        reason.setDescription(description);
        reason.setActive(true);
        reason.setCreatedAt(OffsetDateTime.now(user.getTimeZone().toZoneId()));
        return reasonRepository.save(reason);
    }

    private User saveUser() {
        String suffix = UUID.randomUUID().toString();
        User user = new User();
        user.setEmail("page-" + suffix + "@example.test");
        user.setPassword("test-password");
        return userRepository.save(user);
    }

    private Wallet saveWallet(User user) {
        return walletRepository.save(Wallet.builder()
                .user(user)
                .currencyCode("PEN")
                .balance(BigDecimal.ZERO)
                .primary(true)
                .build());
    }

    private Category saveCategory(User user) {
        Category category = new Category();
        category.setUser(user);
        category.setName("Test category");
        category.setType(TransactionType.SPENT);
        category.setDescription("Pagination test");
        return categoryRepository.save(category);
    }

    private Transaction saveTransaction(Wallet wallet, Category category, OffsetDateTime createdAt) {
        Transaction transaction = new Transaction();
        transaction.setWallet(wallet);
        transaction.setCategory(category);
        transaction.setType(TransactionType.SPENT);
        transaction.setPaymentMethod(PaymentMethod.CASH);
        transaction.setAmount(BigDecimal.ONE);
        transaction.setActive(true);
        transaction.setCreatedAt(createdAt);
        return transactionRepository.save(transaction);
    }

    private Transaction saveTransaction(Wallet wallet, Category category, Reason reason, OffsetDateTime createdAt) {
        Transaction transaction = saveTransaction(wallet, category, createdAt);
        transaction.setReason(reason);
        return transactionRepository.save(transaction);
    }
}
