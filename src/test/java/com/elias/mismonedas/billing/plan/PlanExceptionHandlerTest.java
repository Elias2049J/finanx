package com.elias.mismonedas.billing.plan;

import com.elias.mismonedas.billing.domain.enums.SubscriptionPlan;
import com.elias.mismonedas.shared.config.GlobalExceptionHandler;
import com.elias.mismonedas.shared.i18n.CurrentLocaleProvider;
import com.elias.mismonedas.shared.i18n.JsonMessageCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanExceptionHandlerTest {

    @Test
    void shouldReturnForbiddenInsteadOfFallingThroughToTheGenericHandler() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PlanRequiredController())
                .setControllerAdvice(
                        new GlobalExceptionHandler(mock(JsonMessageCatalog.class), mock(CurrentLocaleProvider.class)),
                        new PlanExceptionHandler())
                .build();

        mvc.perform(get("/plan-required"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAN_REQUIRED"))
                .andExpect(jsonPath("$.current").value("FREE"));
    }

    @RestController
    static class PlanRequiredController {

        @GetMapping("/plan-required")
        void planRequired() {
            throw new PlanRequiredException(
                    new SubscriptionPlan[]{SubscriptionPlan.PREMIUM}, SubscriptionPlan.FREE);
        }
    }
}
