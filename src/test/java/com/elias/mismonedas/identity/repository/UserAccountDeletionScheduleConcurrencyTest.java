package com.elias.mismonedas.identity.repository;

import com.elias.mismonedas.identity.domain.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.messaging.rabbitmq.consumer-auto-startup=false",
        "spring.task.scheduling.enabled=false"
})
class UserAccountDeletionScheduleConcurrencyTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;

    @AfterEach
    void deleteTestUser() {
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    void schedulerRechecksCandidateAfterConcurrentCancellationCommits() throws Exception {
        OffsetDateTime deadline = OffsetDateTime.now().plusSeconds(10);
        User pending = new User();
        pending.setName("Deletion race");
        pending.setLastname("Test");
        pending.setEmail("deletion-race-" + UUID.randomUUID() + "@example.test");
        pending.setPassword("not-used");
        pending.requestDeletion(deadline.minusDays(7), deadline);
        userId = userRepository.saveAndFlush(pending).getId();

        CountDownLatch cancellationUpdated = new CountDownLatch(1);
        CountDownLatch allowCancellationToCommit = new CountDownLatch(1);
        CountDownLatch schedulerQueryStarted = new CountDownLatch(1);
        AtomicReference<Integer> cancellationPid = new AtomicReference<>();
        AtomicReference<Integer> schedulerPid = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> cancellation = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        cancellationPid.set(jdbcTemplate.queryForObject("select pg_backend_pid()", Integer.class));
                        User locked = userRepository.findByIdForUpdate(userId).orElseThrow();
                        assertThat(locked.canCancelDeletionAt(OffsetDateTime.now())).isTrue();
                        locked.cancelDeletion();
                        userRepository.saveAndFlush(locked);
                        cancellationUpdated.countDown();
                        await(allowCancellationToCommit);
                    }));

            assertThat(cancellationUpdated.await(10, TimeUnit.SECONDS)).isTrue();
            Future<List<User>> scheduledCandidates = executor.submit(() ->
                    new TransactionTemplate(transactionManager).execute(status -> {
                        schedulerPid.set(jdbcTemplate.queryForObject("select pg_backend_pid()", Integer.class));
                        schedulerQueryStarted.countDown();
                        return userRepository.findUsersWithExpiredDeletionSchedule(deadline.plusSeconds(1));
                    }));

            assertThat(schedulerQueryStarted.await(10, TimeUnit.SECONDS)).isTrue();
            awaitBlockedBy(cancellationPid.get(), schedulerPid.get(), scheduledCandidates);
            allowCancellationToCommit.countDown();
            cancellation.get(10, TimeUnit.SECONDS);

            assertThat(scheduledCandidates.get(10, TimeUnit.SECONDS)).isEmpty();
        } finally {
            allowCancellationToCommit.countDown();
            executor.shutdownNow();
        }
    }

    private void awaitBlockedBy(int blockingPid, int waitingPid, Future<?> query) throws Exception {
        long timeoutAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < timeoutAt && !query.isDone()) {
            Boolean blockedByCancellation = jdbcTemplate.queryForObject(
                    "select ? = any(pg_blocking_pids(?))", Boolean.class, blockingPid, waitingPid);
            if (Boolean.TRUE.equals(blockedByCancellation)) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Scheduler query did not block on the cancellation row lock");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to finish cancellation transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding the cancellation row lock", exception);
        }
    }
}
