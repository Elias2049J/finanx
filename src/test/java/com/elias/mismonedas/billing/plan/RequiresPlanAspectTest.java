package com.elias.mismonedas.billing.plan;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.elias.mismonedas.billing.domain.enums.SubscriptionPlan;
import com.elias.mismonedas.billing.service.SubscriptionService;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequiresPlanAspectTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private SecurityService securityService;

    @Mock
    private ProceedingJoinPoint pjp;

    @InjectMocks
    private RequiresPlanAspect aspect;

    private Logger aspectLogger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        aspectLogger = (Logger) LoggerFactory.getLogger(RequiresPlanAspect.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        aspectLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        listAppender.stop();
        aspectLogger.detachAppender(listAppender);
    }

    @Nested
    class PlanResolutionTest {

        @Test
        void shouldAllowWhenCurrentUserPlanMatches() throws Throwable {
            givenCurrentUser(1L);
            when(subscriptionService.getActivePlanFor(1L)).thenReturn(SubscriptionPlan.PREMIUM);
            when(pjp.proceed()).thenReturn("ok");

            Object result = aspect.checkPlan(pjp, requiresPlan("premiumOnly"));

            assertThat(result).isEqualTo("ok");
            verify(subscriptionService).getActivePlanFor(1L);
            verify(pjp).proceed();
            assertLogged(Level.INFO, "Acceso permitido para usuario 1 con plan PREMIUM");
        }

        @Test
        void shouldDenyWhenCurrentUserPlanDoesNotMatch() throws Throwable {
            givenCurrentUser(2L);
            when(subscriptionService.getActivePlanFor(2L)).thenReturn(SubscriptionPlan.FREE);

            assertThatThrownBy(() -> aspect.checkPlan(pjp, requiresPlan("premiumOnly")))
                    .isInstanceOf(PlanRequiredException.class)
                    .hasMessageContaining("FREE");

            verify(pjp, never()).proceed();
            assertLogged(Level.WARN, "Acceso denegado para usuario 2");
        }

        @Test
        void shouldLogVerificationInfo() throws Throwable {
            givenCurrentUser(3L);
            when(subscriptionService.getActivePlanFor(3L)).thenReturn(SubscriptionPlan.PREMIUM_TRIAL);
            when(pjp.proceed()).thenReturn("ok");

            aspect.checkPlan(pjp, requiresPlan("premiumOrTrial"));

            assertLogged(Level.INFO, "Verificando plan para usuario 3");
        }

        @Test
        void shouldAllowAnyPlanListedInTheAnnotation() throws Throwable {
            givenCurrentUser(42L);
            when(subscriptionService.getActivePlanFor(42L)).thenReturn(SubscriptionPlan.PREMIUM_TRIAL);
            when(pjp.proceed()).thenReturn("ok");

            aspect.checkPlan(pjp, requiresPlan("premiumOrTrial"));

            verify(subscriptionService).getActivePlanFor(42L);
            verify(pjp).proceed();
        }

        @Test
        void shouldResolveSubjectFromSecurityContextRegardlessOfMethodParams() throws Throwable {
            givenCurrentUser(99L);
            when(subscriptionService.getActivePlanFor(99L)).thenReturn(SubscriptionPlan.PREMIUM);
            when(pjp.proceed()).thenReturn("ok");

            aspect.checkPlan(pjp, requiresPlan("withoutParams"));

            verify(subscriptionService).getActivePlanFor(99L);
            verify(securityService).getCurrentUser();
        }
    }

    private void givenCurrentUser(Long id) {
        User user = new User();
        user.setId(id);
        when(securityService.getCurrentUser()).thenReturn(user);
    }

    private RequiresPlan requiresPlan(String methodName) throws NoSuchMethodException {
        Method method = TestService.class.getMethod(methodName);
        return method.getAnnotation(RequiresPlan.class);
    }

    private void assertLogged(Level level, String message) {
        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs)
                .anyMatch(e -> e.getLevel().equals(level) && e.getFormattedMessage().contains(message));
    }

    @SuppressWarnings("unused")
    private static class TestService {

        @RequiresPlan(SubscriptionPlan.PREMIUM)
        public void premiumOnly() {}

        @RequiresPlan({SubscriptionPlan.PREMIUM, SubscriptionPlan.PREMIUM_TRIAL})
        public void premiumOrTrial() {}

        @RequiresPlan(SubscriptionPlan.PREMIUM)
        public void withoutParams() {}

    }

}
