package com.elias.mismonedas.ledger.service.impl;

import com.elias.mismonedas.planning.service.SavingGoalService;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import com.elias.mismonedas.wallets.service.WalletService;
import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.domain.entity.Reason;
import com.elias.mismonedas.catalog.service.CategoryService;
import com.elias.mismonedas.catalog.service.ReasonResolver;
import com.elias.mismonedas.document.domain.entity.Receipt;
import com.elias.mismonedas.document.repository.ReceiptRepository;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.ledger.domain.entity.Transaction;
import com.elias.mismonedas.ledger.domain.event.TransactionRegisteredEvent;
import com.elias.mismonedas.shared.domain.enums.PaymentMethod;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import com.elias.mismonedas.ledger.dto.transaction.TransactionRequest;
import com.elias.mismonedas.ledger.dto.transaction.TransactionResponse;
import com.elias.mismonedas.ledger.mapper.TransactionMapper;
import com.elias.mismonedas.ledger.repository.TransactionRepository;
import com.elias.mismonedas.shared.exception.ConflictException;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private WalletService walletService;
    @Mock private CategoryService categoryService;
    @Mock private SavingGoalService savingGoalService;
    @Mock private ReceiptRepository receiptRepository;
    @Mock private TransactionMapper transactionMapper;
    @Mock private ReasonResolver reasonResolver;
    @Mock private CurrencyConverter currencyConverter;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Captor private ArgumentCaptor<Receipt> receiptCaptor;

    private TransactionServiceImpl service;

    private User user;
    private Wallet wallet;
    private Category category;
    private Reason reason;
    private TransactionRequest request;
    private Transaction transaction;
    private Transaction recordedTransaction;
    private TransactionResponse response;

    @BeforeEach
    void setUp() {
        service = new TransactionServiceImpl(
                transactionRepository, walletService, categoryService,
                savingGoalService, receiptRepository, transactionMapper, reasonResolver,
                currencyConverter, securityService, eventPublisher);

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

        recordedTransaction = new Transaction();
        recordedTransaction.setId(999L);
        recordedTransaction.setType(TransactionType.SPENT);
        recordedTransaction.setAmount(new BigDecimal("50.00"));
        recordedTransaction.setWallet(wallet);
        recordedTransaction.setCategory(category);
        recordedTransaction.setReason(reason);

        response = new TransactionResponse();
        response.setId(999L);

        lenient().when(securityService.getCurrentUser()).thenReturn(user);
    }

    @Nested
    class CreateTest {

        @Test
        void shouldCreateTransactionWithoutReceipt() throws Exception {
            when(transactionMapper.toEntity(request)).thenReturn(transaction);
            when(walletService.getOwned(10L, 1L)).thenReturn(wallet);
            when(categoryService.getOwned(100L, 1L)).thenReturn(category);
            when(reasonResolver.resolveOrCreate(anyLong(), any(), any())).thenReturn(Optional.of(reason));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(recordedTransaction);
            when(transactionMapper.toResponse(recordedTransaction)).thenReturn(response);

            TransactionResponse result = service.create(request, null);

            assertThat(result.getId()).isEqualTo(999L);
            verify(receiptRepository, never()).findByIdAndUser_Id(anyLong(), anyLong());
            verify(receiptRepository, never()).save(any());
            verify(walletService).updateBalance(1L, 10L, TransactionType.SPENT, new BigDecimal("50.00"));
            verify(eventPublisher).publishEvent(isA(TransactionRegisteredEvent.class));
        }

        @Test
        void shouldPropagateScheduleIdWhenCreatedByScheduler() throws Exception {
            when(transactionMapper.toEntity(request)).thenReturn(transaction);
            when(walletService.getOwned(10L, 1L)).thenReturn(wallet);
            when(categoryService.getOwned(100L, 1L)).thenReturn(category);
            when(reasonResolver.resolveOrCreate(anyLong(), any(), any())).thenReturn(Optional.of(reason));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

            service.create(1L, 55L, request, "key-schedule");

            assertThat(transaction.getScheduleId()).isEqualTo(55L);
            ArgumentCaptor<TransactionRegisteredEvent> eventCaptor =
                    ArgumentCaptor.forClass(TransactionRegisteredEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().scheduleId()).isEqualTo(55L);
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
            when(walletService.getOwned(10L, 1L)).thenReturn(wallet);
            when(categoryService.getOwned(100L, 1L)).thenReturn(category);
            when(reasonResolver.resolveOrCreate(anyLong(), any(), any())).thenReturn(Optional.of(reason));
            when(receiptRepository.findByIdAndUser_Id(777L, 1L)).thenReturn(Optional.of(receipt));
            when(transactionRepository.save(any(Transaction.class))).thenReturn(recordedTransaction);
            when(transactionMapper.toResponse(recordedTransaction)).thenReturn(response);

            TransactionResponse result = service.create(request, null);

            assertThat(result.getId()).isEqualTo(999L);
            verify(receiptRepository).findByIdAndUser_Id(777L, 1L);
            verify(receiptRepository).save(receiptCaptor.capture());
            assertThat(receiptCaptor.getValue().getTransaction()).isEqualTo(recordedTransaction);
        }

        @Test
        void shouldReturnExistingTransactionOnIdempotencyKeyReplay() throws Exception {
            Transaction existing = new Transaction();
            existing.setId(999L);
            existing.setWallet(wallet);
            existing.setUser(user);
            TransactionResponse existingResponse = new TransactionResponse();
            existingResponse.setId(999L);

            when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));
            when(transactionMapper.toResponse(existing)).thenReturn(existingResponse);

            TransactionResponse result = service.create(request, "key-1");

            assertThat(result.getId()).isEqualTo(999L);
            verify(transactionRepository, never()).save(any());
        }

        @Test
        void shouldRejectReplayOfIdempotencyKeyOwnedByAnotherUser() {
            User other = new User();
            other.setId(2L);
            Wallet otherWallet = new Wallet();
            otherWallet.setUser(other);

            Transaction existing = new Transaction();
            existing.setId(888L);
            existing.setWallet(otherWallet);
            existing.setUser(other);

            when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.create(request, "key-1"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("another user");
        }

        @Test
        void shouldReturnExistingTransactionWhenRaceLosesOnUniqueConstraint() throws Exception {
            Transaction existing = new Transaction();
            existing.setId(777L);
            existing.setWallet(wallet);
            existing.setUser(user);
            TransactionResponse existingResponse = new TransactionResponse();
            existingResponse.setId(777L);

            when(transactionMapper.toEntity(request)).thenReturn(transaction);
            when(walletService.getOwned(10L, 1L)).thenReturn(wallet);
            when(categoryService.getOwned(100L, 1L)).thenReturn(category);
            when(reasonResolver.resolveOrCreate(anyLong(), any(), any())).thenReturn(Optional.of(reason));
            when(transactionRepository.findByIdempotencyKey("key-1"))
                    .thenReturn(Optional.empty(), Optional.of(existing));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenThrow(new DataIntegrityViolationException("uq clave_idempotencia"));
            when(transactionMapper.toResponse(existing)).thenReturn(existingResponse);

            TransactionResponse result = service.create(request, "key-1");

            assertThat(result.getId()).isEqualTo(777L);
        }

        @Test
        void shouldThrowWhenReceiptNotFound() {
            request.setReceiptId(999L);

            when(transactionMapper.toEntity(request)).thenReturn(transaction);
            when(walletService.getOwned(10L, 1L)).thenReturn(wallet);
            when(categoryService.getOwned(100L, 1L)).thenReturn(category);
            when(reasonResolver.resolveOrCreate(anyLong(), any(), any())).thenReturn(Optional.of(reason));
            when(receiptRepository.findByIdAndUser_Id(999L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(request, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Recibo no encontrado");
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
            when(walletService.getOwned(10L, 1L)).thenReturn(wallet);
            when(categoryService.getOwned(100L, 1L)).thenReturn(category);
            when(reasonResolver.resolveOrCreate(anyLong(), any(), any())).thenReturn(Optional.of(reason));
            when(receiptRepository.findByIdAndUser_Id(777L, 1L)).thenReturn(Optional.of(linkedReceipt));

            assertThatThrownBy(() -> service.create(request, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already linked");
        }
    }
}
