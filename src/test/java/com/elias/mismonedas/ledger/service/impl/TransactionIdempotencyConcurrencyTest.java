package com.elias.mismonedas.ledger.service.impl;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.dto.transaction.TransactionRequest;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.wallets.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.messaging.rabbitmq.consumer-auto-startup=false")
class TransactionIdempotencyConcurrencyTest {

    @Autowired private TransactionServiceImpl transactionService;
    @Autowired private UserRepository userRepository;
    @Autowired private WalletRepository walletRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private User user;
    private Wallet wallet;
    private Category category;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("idem-" + UUID.randomUUID() + "@example.test");
        user.setPassword("test-password");
        user = userRepository.save(user);

        wallet = walletRepository.save(Wallet.builder()
                .user(user)
                .currencyCode("PEN")
                .balance(BigDecimal.ZERO)
                .primary(true)
                .build());

        category = new Category();
        category.setUser(user);
        category.setName("Idempotency test");
        category.setType(TransactionType.INCOME);
        category.setDescription("Concurrency test");
        category = categoryRepository.save(category);
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            transactionRepository.deleteAllByWallet_User_Id(user.getId());
            walletRepository.deleteById(wallet.getId());
            categoryRepository.delete(category);
            userRepository.delete(user);
        });
    }

    @Test
    void shouldReturnTheSameTransactionWhenTwoRequestsRaceWithTheSameKey() throws Exception {
        String key = "race-" + UUID.randomUUID();
        TransactionRequest request = request();

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return transactionService.createWithRecovery(user.getId(), request, key).getId();
            }));
        }
        start.countDown();

        Set<Long> ids = new HashSet<>();
        for (Future<Long> future : futures) {
            ids.add(future.get(15, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(ids).hasSize(1);
        assertThat(transactionRepository.findByIdempotencyKey(key))
                .hasValueSatisfying(saved -> assertThat(saved.getId()).isIn(ids));
    }

    @Test
    void shouldRecoverAfterUniqueViolationWhenTheWinnerCommitsLate() throws Exception {
        String key = "late-" + UUID.randomUUID();
        TransactionRequest request = request();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch winnerInserted = new CountDownLatch(1);
        CountDownLatch commitWinner = new CountDownLatch(1);

        Future<Long> winner = pool.submit(() -> transactionTemplate.execute(status -> {
            Transaction transaction = new Transaction();
            transaction.setWallet(wallet);
            transaction.setUser(user);
            transaction.setCategory(category);
            transaction.setType(TransactionType.INCOME);
            transaction.setPaymentMethod(PaymentMethod.CASH);
            transaction.setAmount(new BigDecimal("10.00"));
            transaction.setIdempotencyKey(key);
            transaction.setActive(true);
            Transaction saved = transactionRepository.save(transaction);
            winnerInserted.countDown();
            try {
                commitWinner.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return saved.getId();
        }));

        assertThat(winnerInserted.await(10, TimeUnit.SECONDS)).isTrue();

        Future<Long> loser = pool.submit(() ->
                transactionService.createWithRecovery(user.getId(), request, key).getId());

        Thread.sleep(500);
        commitWinner.countDown();

        Long winnerId = winner.get(15, TimeUnit.SECONDS);
        Long loserId = loser.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(loserId).isEqualTo(winnerId);
        assertThat(transactionRepository.findByIdempotencyKey(key))
                .hasValueSatisfying(saved -> assertThat(saved.getId()).isEqualTo(winnerId));
    }

    private TransactionRequest request() {
        TransactionRequest request = new TransactionRequest();
        request.setWalletId(wallet.getId());
        request.setCategoryId(category.getId());
        request.setType(TransactionType.INCOME);
        request.setAmount(new BigDecimal("10.00"));
        request.setPaymentMethod(PaymentMethod.CASH);
        return request;
    }
}
