package com.elias.mismonedas.identity.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.UserState;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.service.UserService;
import com.elias.mismonedas.document.domain.entity.Image;
import com.elias.mismonedas.document.domain.entity.Receipt;
import com.elias.mismonedas.document.outbox.ReceiptScanOutbox;
import com.elias.mismonedas.document.outbox.ReceiptScanOutboxState;
import com.elias.mismonedas.document.outbox.persistence.ReceiptScanOutboxRepository;
import com.elias.mismonedas.document.port.ObjectStoragePort;
import com.elias.mismonedas.document.repository.ImageRepository;
import com.elias.mismonedas.document.repository.ReceiptRepository;
import com.elias.mismonedas.billing.domain.entity.Payment;
import com.elias.mismonedas.billing.domain.entity.Subscription;
import com.elias.mismonedas.billing.domain.enums.BillingCycle;
import com.elias.mismonedas.billing.domain.enums.PaymentProvider;
import com.elias.mismonedas.billing.domain.enums.PaymentState;
import com.elias.mismonedas.billing.domain.enums.SubscriptionPlan;
import com.elias.mismonedas.billing.domain.enums.SubscriptionState;
import com.elias.mismonedas.billing.repository.PaymentRepository;
import com.elias.mismonedas.billing.repository.SubscriptionRepository;
import com.elias.mismonedas.chat.domain.entity.ChatMessage;
import com.elias.mismonedas.chat.domain.entity.ChatSession;
import com.elias.mismonedas.chat.domain.enums.ChatRole;
import com.elias.mismonedas.chat.repository.ChatMessageRepository;
import com.elias.mismonedas.chat.repository.ChatSessionRepository;
import com.elias.mismonedas.insights.domain.entity.UserDataContext;
import com.elias.mismonedas.insights.repository.UserDataContextRepository;
import com.elias.mismonedas.identity.domain.entity.EmailVerificationChallenge;
import com.elias.mismonedas.identity.domain.enums.EmailVerificationPurpose;
import com.elias.mismonedas.identity.repository.EmailVerificationChallengeRepository;
import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.notifications.domain.entity.Notification;
import com.elias.mismonedas.notifications.domain.enums.NotificationState;
import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.notifications.outbox.NotificationOutbox;
import com.elias.mismonedas.notifications.outbox.OutboxState;
import com.elias.mismonedas.notifications.outbox.persistence.NotificationOutboxRepository;
import com.elias.mismonedas.notifications.repository.NotificationRepository;
import com.elias.mismonedas.scheduling.domain.entity.RecurrenceRule;
import com.elias.mismonedas.scheduling.domain.entity.TransactionSchedule;
import com.elias.mismonedas.scheduling.domain.enums.RecurrenceType;
import com.elias.mismonedas.scheduling.repository.RecurrenceRuleRepository;
import com.elias.mismonedas.scheduling.repository.ScheduleRepository;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.wallets.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(AccountDeletionCoreDataIntegrationTest.StorageTestConfig.class)
@SpringBootTest(properties = {
        "app.messaging.rabbitmq.consumer-auto-startup=false",
        "spring.task.scheduling.enabled=false"
})
class AccountDeletionCoreDataIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserDataContextRepository userDataContextRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private RecurrenceRuleRepository recurrenceRuleRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private ReceiptScanOutboxRepository receiptScanOutboxRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Autowired
    private EmailVerificationChallengeRepository challengeRepository;

    @Autowired
    private TestObjectStoragePort objectStoragePort;

    @Autowired
    private UserService userService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long userId;

    @BeforeEach
    void cleanUpAbandonedFixtures() {
        userRepository.findAll().stream()
                .filter(user -> user.getEmail() != null
                        && user.getEmail().startsWith("account-deletion-")
                        && "Deletion".equals(user.getName())
                        && "Integration".equals(user.getLastname()))
                .map(User::getId)
                .toList()
                .forEach(this::deleteFixture);
    }

    @AfterEach
    void cleanUpTestData() {
        if (userId != null) {
            deleteFixture(userId);
        }
        objectStoragePort.deletedObjects.clear();
    }

    @Test
    @Transactional
    void permanentlyDeletesAccountWithWalletAndFinancialContext() {
        OffsetDateTime now = OffsetDateTime.now();
        User user = new User();
        user.setName("Deletion");
        user.setLastname("Integration");
        user.setEmail("account-deletion-" + UUID.randomUUID() + "@example.test");
        user.setPassword("not-used");
        user.setEmailVerified(true);
        user.setState(UserState.ENABLED);
        userId = userRepository.saveAndFlush(user).getId();

        Wallet wallet = walletRepository.saveAndFlush(Wallet.builder()
                .user(user)
                .currencyCode("PEN")
                .balance(new BigDecimal("12.50"))
                .primary(true)
                .build());
        Category category = new Category();
        category.setUser(user);
        category.setName("Deletion test");
        category.setType(TransactionType.SPENT);
        category = categoryRepository.saveAndFlush(category);
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setWallet(wallet);
        transaction.setCategory(category);
        transaction.setType(TransactionType.SPENT);
        transaction.setPaymentMethod(PaymentMethod.CASH);
        transaction.setAmount(new BigDecimal("5.00"));
        transaction = transactionRepository.saveAndFlush(transaction);

        Image image = imageRepository.saveAndFlush(Image.builder()
                .user(user)
                .filename("receipt.jpg")
                .mediaType("image/jpeg")
                .bucketName("test-bucket")
                .objectKey("images/" + userId + "/receipt.jpg")
                .sizeBytes(128L)
                .build());
        Receipt receipt = receiptRepository.saveAndFlush(Receipt.builder()
                .user(user)
                .transaction(transaction)
                .image(image)
                .status(com.elias.mismonedas.document.domain.enums.ReceiptScanStatus.PENDING)
                .build());
        ReceiptScanOutbox scanOutbox = receiptScanOutboxRepository.saveAndFlush(ReceiptScanOutbox.builder()
                .receiptId(receipt.getId())
                .bucket(image.getBucketName())
                .objectKey(image.getObjectKey())
                .state(ReceiptScanOutboxState.PENDING)
                .createdAt(now)
                .build());

        RecurrenceRule recurrenceRule = new RecurrenceRule();
        recurrenceRule.setUser(user);
        recurrenceRule.setRecurrenceType(RecurrenceType.DAILY);
        recurrenceRule.setInterval(1);
        recurrenceRule.setStart(now.minusDays(8));
        recurrenceRuleRepository.saveAndFlush(recurrenceRule);

        TransactionSchedule schedule = new TransactionSchedule();
        schedule.setUser(user);
        schedule.setCategory(category);
        schedule.setNextRunAt(now.plusDays(1));
        schedule.setWallet(wallet);
        schedule.setAmount(new BigDecimal("3.00"));
        schedule.setPaymentMethod(PaymentMethod.CASH);
        schedule.setTransactionType(TransactionType.SPENT);
        schedule.setRecurrenceRule(recurrenceRule);
        scheduleRepository.saveAndFlush(schedule);

        Subscription subscription = subscriptionRepository.saveAndFlush(Subscription.builder()
                .user(user)
                .plan(SubscriptionPlan.PREMIUM)
                .billingCycle(BillingCycle.MONTHLY)
                .state(SubscriptionState.PENDING)
                .amount(BigDecimal.ZERO)
                .currencyCode("PEN")
                .autoRenew(false)
                .createdAt(now)
                .build());
        Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                .user(user)
                .subscription(subscription)
                .amount(BigDecimal.ZERO)
                .currencyCode("PEN")
                .provider(PaymentProvider.MERCADO_PAGO)
                .state(PaymentState.PENDING)
                .createdAt(now)
                .build());

        ChatSession chatSession = new ChatSession();
        chatSession.setUser(user);
        chatSession.setFinancialContext("private financial context");
        chatSession = chatSessionRepository.saveAndFlush(chatSession);
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSession(chatSession);
        chatMessage.setTurnNumber(1);
        chatMessage.setRole(ChatRole.USER);
        chatMessage.setMessage("private message");
        chatMessage = chatMessageRepository.saveAndFlush(chatMessage);

        Notification notification = notificationRepository.saveAndFlush(Notification.builder()
                .user(user)
                .type(NotificationType.INFO)
                .message("private notification")
                .createdAt(now)
                .state(NotificationState.SCHEDULED)
                .build());
        NotificationOutbox notificationOutbox = notificationOutboxRepository.saveAndFlush(NotificationOutbox.builder()
                .notificationId(notification.getId())
                .type(NotificationType.INFO)
                .payload("private notification payload")
                .state(OutboxState.PENDING)
                .createdAt(now)
                .retryCount(0)
                .build());

        EmailVerificationChallenge verificationChallenge = challengeRepository.saveAndFlush(
                EmailVerificationChallenge.builder()
                        .email(user.getEmail())
                        .purpose(EmailVerificationPurpose.REGISTRATION)
                        .codeHash("f".repeat(64))
                        .expiresAt(now.plusMinutes(15))
                        .lastSentAt(now)
                        .createdAt(now)
                        .build());

        UserDataContext context = new UserDataContext();
        context.setUser(user);
        context.setCurrentData("financial context");
        userDataContextRepository.saveAndFlush(context);

        user.requestDeletion(now.minusDays(8), now.minusDays(1));
        userRepository.saveAndFlush(user);

        userService.processScheduledAccountDeletions();

        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(walletRepository.findByUserId(userId)).isEmpty();
        assertThat(userDataContextRepository.findByUser_Id(userId)).isEmpty();
        assertThat(transactionRepository.findById(transaction.getId())).isEmpty();
        assertThat(categoryRepository.findAllByUser_Id(userId)).isEmpty();
        assertThat(recurrenceRuleRepository.findAll()).noneMatch(rule -> userId.equals(rule.getUser().getId()));
        assertThat(scheduleRepository.findAllByUser_Id(userId)).isEmpty();
        assertThat(receiptRepository.findById(receipt.getId())).isEmpty();
        assertThat(imageRepository.findById(image.getId())).isEmpty();
        assertThat(receiptScanOutboxRepository.findById(scanOutbox.getId())).isEmpty();
        assertThat(objectStoragePort.deletedObjects).contains("test-bucket/images/" + userId + "/receipt.jpg");
        assertThat(paymentRepository.findById(payment.getId())).isEmpty();
        assertThat(subscriptionRepository.findById(subscription.getId())).isEmpty();
        assertThat(chatMessageRepository.findById(chatMessage.getId())).isEmpty();
        assertThat(chatSessionRepository.findById(chatSession.getId())).isEmpty();
        assertThat(notificationRepository.findById(notification.getId())).isEmpty();
        assertThat(notificationOutboxRepository.findById(notificationOutbox.getId())).isEmpty();
        assertThat(challengeRepository.findByEmailAndPurposeForUpdate(
                verificationChallenge.getEmail(), verificationChallenge.getPurpose())).isEmpty();
    }

    private void deleteReceipts(Long fixtureUserId) {
        List<Long> receiptIds = receiptRepository.findIdsByUserId(fixtureUserId);
        if (!receiptIds.isEmpty()) {
            receiptScanOutboxRepository.deleteAllByReceiptIdIn(receiptIds);
            receiptRepository.deleteAllRowsByUserId(fixtureUserId);
        }
    }

    private void deleteFixture(Long fixtureUserId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            deleteReceipts(fixtureUserId);
            notificationOutboxRepository.deleteAllForUser(fixtureUserId);
            notificationRepository.deleteAllByUser_Id(fixtureUserId);
            chatMessageRepository.deleteAllBySessionUserId(fixtureUserId);
            chatSessionRepository.deleteAllByUser_Id(fixtureUserId);
            paymentRepository.deleteAllByUser_Id(fixtureUserId);
            subscriptionRepository.deleteAllByUser_Id(fixtureUserId);
            userRepository.findById(fixtureUserId).ifPresent(user -> {
                challengeRepository.deleteByEmailAndPurpose(user.getEmail(), EmailVerificationPurpose.REGISTRATION);
                challengeRepository.deleteByEmailAndPurpose(user.getEmail(), EmailVerificationPurpose.EXISTING_ACCOUNT);
            });
            transactionRepository.deleteAllByWallet_User_Id(fixtureUserId);
            imageRepository.deleteAll(imageRepository.findAllByUser_Id(fixtureUserId));
            scheduleRepository.deleteAllByUser_Id(fixtureUserId);
            recurrenceRuleRepository.deleteAllByUser_Id(fixtureUserId);
            userDataContextRepository.findByUser_Id(fixtureUserId).ifPresent(userDataContextRepository::delete);
            categoryRepository.deleteAllByUser_Id(fixtureUserId);
            walletRepository.deleteAll(walletRepository.findByUserId(fixtureUserId));
            if (userRepository.existsById(fixtureUserId)) {
                userRepository.deleteById(fixtureUserId);
            }
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StorageTestConfig {

        @Bean
        @Primary
        TestObjectStoragePort testObjectStoragePort() {
            return new TestObjectStoragePort();
        }
    }

    static class TestObjectStoragePort implements ObjectStoragePort {

        private final List<String> deletedObjects = new CopyOnWriteArrayList<>();

        @Override
        public void putObject(String bucket, String objectKey, String contentType, String filename, byte[] data) {
        }

        @Override
        public URI createPresignedGetUrl(String bucket, String objectKey, Duration expiration) {
            return URI.create("https://example.test/" + bucket + "/" + objectKey);
        }

        @Override
        public void deleteObject(String bucket, String objectKey) {
            deletedObjects.add(bucket + "/" + objectKey);
        }
    }
}
