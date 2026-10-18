package com.elias.mismonedas.ledger.domain.event;

/**
 * Se publica cuando una transaccion cambia (alta, edicion o deshabilitado) para que
 * {@code planning} recalcule sus derivados sin que {@code ledger} lo llame.
 *
 * <p>F5: antes {@code TransactionServiceImpl} importaba {@code BudgetService} y
 * {@code SavingGoalService} y ejecutaba {@code checkAll*} tras cada escritura, acoplando
 * {@code ledger -> planning}. El evento se publica sincrono (mismo hilo, misma transaccion),
 * por lo que el recalculo sigue siendo atomico con la escritura que lo dispara.
 *
 * <p>No lleva montos ni monedas: {@code planning} recalcula leyendo de su fuente
 * ({@code ledger}), que es la unica dueña de los importes.
 */
public record TransactionChangedEvent(Long transactionId, Long userId) {
}
