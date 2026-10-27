package com.elias.mismonedas.wallets.repository;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.Role;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.wallets.domain.entity.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.messaging.rabbitmq.consumer-auto-startup=false",
        "spring.task.scheduling.enabled=false"
})
class WalletSameCurrencyPersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Test
    @Transactional
    void persistsMultipleWalletsForTheSameUserAndCurrency() {
        User user = new User();
        user.setName("Wallet");
        user.setLastname("Fixture");
        user.setEmail("wallet-same-currency-" + UUID.randomUUID() + "@example.test");
        user.setPassword("not-used");
        user.setRole(Role.USER);
        User savedUser = userRepository.saveAndFlush(user);

        walletRepository.saveAndFlush(Wallet.builder()
                .user(savedUser)
                .currencyCode("PEN")
                .balance(new BigDecimal("10.00"))
                .primary(true)
                .build());
        walletRepository.saveAndFlush(Wallet.builder()
                .user(savedUser)
                .currencyCode("PEN")
                .alias("Efectivo")
                .balance(new BigDecimal("20.00"))
                .primary(false)
                .build());

        assertThat(walletRepository.findAllByUser_IdAndCurrencyCode(savedUser.getId(), "PEN"))
                .hasSize(2)
                .extracting(Wallet::getAlias)
                .containsExactlyInAnyOrder("Principal", "Efectivo");
    }
}
