package com.elias.mismonedas.billing.listener;

import com.elias.mismonedas.billing.service.BillingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BillingListenerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(BillingService.class, () -> mock(BillingService.class))
            .withUserConfiguration(BillingListener.class);

    @Test
    void shouldNotRegisterListenerByDefault() {
        // Sin la property no se regala PREMIUM en produccion.
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(BillingListener.class));
    }

    @Test
    void shouldNotRegisterListenerWhenPropertyIsFalse() {
        contextRunner.withPropertyValues("app.billing.auto-premium=false")
                .run(context -> assertThat(context).doesNotHaveBean(BillingListener.class));
    }

    @Test
    void shouldRegisterListenerWhenPropertyIsTrue() {
        contextRunner.withPropertyValues("app.billing.auto-premium=true")
                .run(context -> assertThat(context).hasSingleBean(BillingListener.class));
    }
}
