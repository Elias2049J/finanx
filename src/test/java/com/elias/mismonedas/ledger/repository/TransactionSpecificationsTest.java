package com.elias.mismonedas.ledger.repository;

import com.elias.mismonedas.accounts.domain.entity.Wallet;
import com.elias.mismonedas.accounts.repository.WalletRepository;
import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.PaymentMethod;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TransactionSpecificationsTest {

    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private CategoryRepository categoryRepository;
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
                        range.startInclusive(userZone), range.endExclusive(userZone)),
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).extracting(Transaction::getId).containsExactly(lastIncluded.getId());
        assertThat(result.getSize()).isEqualTo(1);
    }

    private User saveUser() {
        String suffix = UUID.randomUUID().toString();
        User user = new User();
        user.setEmail("page-" + suffix + "@example.test");
        user.setUsername("page-" + suffix);
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
}
