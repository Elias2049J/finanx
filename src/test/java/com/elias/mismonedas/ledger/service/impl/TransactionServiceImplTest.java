package com.elias.mismonedas.ledger.service.impl;

import com.elias.mismonedas.accounts.domain.entity.Wallet;
import com.elias.mismonedas.accounts.repository.WalletRepository;
import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.domain.entity.Reason;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.catalog.service.ReasonResolver;
import com.elias.mismonedas.document.domain.entity.Receipt;
import com.elias.mismonedas.document.repository.ReceiptRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.insights.service.UserDataContextService;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.enums.PaymentMethod;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.domain.event.TransactionChangedEvent;
import com.elias.mismonedas.ledger.dto.transaction.TransactionRequest;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.ledger.mapper.TransactionMapper;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.ledger.service.impl.TransactionServiceImpl;
import com.elias.mismonedas.planning.repository.SavingGoalRepository;
import com.elias.mismonedas.shared.money.CurrencyConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private ReceiptRepository receiptRepository;
    @Mock private TransactionMapper transactionMapper;
    @Mock private ReasonResolver reasonResolver;
    @Mock private SavingGoalRepository sgRepository;
    @Mock private UserDataContextService userDataContextService;
    @Mock private CurrencyConverter currencyConverter;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Captor private ArgumentCaptor<Receipt> receiptCaptor;
    @Captor private ArgumentCaptor<TransactionChangedEvent> eventCaptor;

    private TransactionServiceImpl service;

    private User user;
    private Wallet wallet;
    private Category category;
    private Reason reason;
    private TransactionRequest request;
    private Transaction transaction;
    private Transaction savedTransaction;
    private TransactionResponse response;

    @BeforeEach
    void setUp() {
        service = new TransactionServiceImpl(
                transactionRepository, walletRepository, categoryRepository, userRepository,
                receiptRepository, transactionMapper, reasonResolver, sgRepository,
                userDataContextService, currencyConverter, securityService, eventPublisher);

        user = new User();
        user.setId(1L);
        user.setPreferences(UserPreferences.builder().timeZone(TimeZone.AMERICA_LIMA).build());

        wallet = new Wallet();
        wallet.setId(10L);
        wallet.setUser(user);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setCurrencyCode("PEN");

        category = new Category();
        category.setId(100L);
        category.setType(TransactionType.SPENT);
        category.setUser(user);

        reason = new Reason();
        reason.setId(500L);
        reason.setDescription("Test reason");

        request = new TransactionRequest();
        request.setWalletId(10L);
        request.setCategoryId(100L);
        request.setAmount(new BigDecimal("50.00"));
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setType(TransactionType.SPENT);

        transaction = new Transaction();
        transaction.setType(TransactionType.SPENT);
        transaction.setAmount(new BigDecimal("50.00"));

        savedTransaction = new Transaction();
        savedTransaction.setId(999L);
        savedTransaction.setType(TransactionType.SPENT);
        savedTransaction.setAmount(new BigDecimal("50.00"));
        savedTransaction.setWallet(wallet);
        savedTransaction.setCategory(category);
        savedTransaction.setReason(reason);

        response = new TransactionResponse();
        response.setId(999L);
    }

    @Nested
    class CreateTest {

        @Test
        void shouldCreateTransactionWithoutReceipt() throws Exception {
            when(transactionMapper.toEntity(request)).thenReturn(transaction);
            when(walletRepository.findById(10L)).thenReturn(Optional.of(wallet));
            when(categoryRepository.findById(100L)).thenReturn(Optional.of(category));
            when(reasonResolver.resolveOrCreate(anyLong(), any(), any())).thenReturn(Optional.of(reason));
            when(transactionRepository.save(any())).thenReturn(savedTransaction);
            when(transactionMapper.toResponse(savedTransaction)).thenReturn(response);

            TransactionResponse result = service.create(request);

            assertThat(result.getId()).isEqualTo(999L);
            verify(receiptRepository, never()).findByIdAndUser_Id(anyLong(), anyLong());
            verify(receiptRepository, never()).save(any());
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().transactionId()).isEqualTo(999L);
            assertThat(eventCaptor.getValue().userId()).isEqualTo(1L);
            verify(userDataContextService).updateUserDataContext(1L);
        }

        @Test
        void shouldCreateTransactionAndLinkReceipt() throws Exception {
            request.setReceiptId(777L);

            Receipt receipt = Receipt.builder()
                    .id(777L)
                    .user(user)
                    .rawOcrText("TOTAL S/. 50.00")
                    .build();

            when(transactionMapper.toEntity(request)).thenReturn(transaction);
            when(walletRepository.findById(10L)).thenReturn(Optional.of(wallet));
            when(categoryRepository.findById(100L)).thenReturn(Optional.of(category));
            when(reasonResolver.resolveOrCreate(anyLong(), any(), any())).thenReturn(Optional.of(reason));
            when(transactionRepository.save(any())).thenReturn(savedTransaction);
            when(receiptRepository.findByIdAndUser_Id(777L, 1L)).thenReturn(Optional.of(receipt));
            when(transactionMapper.toResponse(savedTransaction)).thenReturn(response);

            TransactionResponse result = service.create(request);

            assertThat(result.getId()).isEqualTo(999L);
            verify(receiptRepository).findByIdAndUser_Id(777L, 1L);
            verify(receiptRepository).save(receiptCaptor.capture());
            assertThat(receiptCaptor.getValue().getTransaction()).isEqualTo(savedTransaction);
        }

        @Test
        void shouldThrowWhenReceiptNotFound() {
            request.setReceiptId(999L);

            when(transactionMapper.toEntity(request)).thenReturn(transaction);
            when(walletRepository.findById(10L)).thenReturn(Optional.of(wallet));
            when(categoryRepository.findById(100L)).thenReturn(Optional.of(category));
            when(reasonResolver.resolveOrCreate(anyLong(), any(), any())).thenReturn(Optional.of(reason));
            when(transactionRepository.save(any())).thenReturn(savedTransaction);
            when(receiptRepository.findByIdAndUser_Id(999L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Receipt not found");
        }

        @Test
        void shouldThrowWhenReceiptAlreadyLinked() {
            request.setReceiptId(777L);

            Receipt linkedReceipt = Receipt.builder()
                    .id(777L)
                    .user(user)
                    .transaction(mock(Transaction.class))
                    .rawOcrText("TOTAL S/. 50.00")
                    .build();

            when(transactionMapper.toEntity(request)).thenReturn(transaction);
            when(walletRepository.findById(10L)).thenReturn(Optional.of(wallet));
            when(categoryRepository.findById(100L)).thenReturn(Optional.of(category));
            when(reasonResolver.resolveOrCreate(anyLong(), any(), any())).thenReturn(Optional.of(reason));
            when(transactionRepository.save(any())).thenReturn(savedTransaction);
            when(receiptRepository.findByIdAndUser_Id(777L, 1L)).thenReturn(Optional.of(linkedReceipt));

            assertThatThrownBy(() -> service.create(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already linked");
        }
    }
}


