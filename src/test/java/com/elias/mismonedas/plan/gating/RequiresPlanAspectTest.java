package com.elias.mismonedas.plan.gating;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.elias.mismonedas.entity.User;
import com.elias.mismonedas.entity.enums.SubscriptionPlan;
import com.elias.mismonedas.service.SubscriptionService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
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
        aspectLogger.detachAndStopAllAppenders();
    }

    @Nested
    class CheckPlanTest {

        @Test
        void shouldProceedWhenPlanIsAllowed() throws Throwable {
            Method method = TestService.class.getMethod("premiumOnly", Long.class);
            MethodSignature signature = mockSignature(method);
            when(pjp.getSignature()).thenReturn(signature);
            when(pjp.getArgs()).thenReturn(new Object[]{1L});
            when(pjp.proceed()).thenReturn("ok");
            when(subscriptionService.getActivePlanFor(1L)).thenReturn(SubscriptionPlan.PREMIUM);

            Object result = aspect.checkPlan(pjp, method.getAnnotation(RequiresPlan.class));

            assertThat(result).isEqualTo("ok");
            verify(subscriptionService).getActivePlanFor(1L);
            verify(pjp).proceed();
            assertLogged(Level.INFO, "Acceso permitido para usuario 1 con plan PREMIUM");
        }

        @Test
        void shouldThrowWhenPlanIsNotAllowed() throws Throwable {
            Method method = TestService.class.getMethod("premiumOnly", Long.class);
            MethodSignature signature = mockSignature(method);
            when(pjp.getSignature()).thenReturn(signature);
            when(pjp.getArgs()).thenReturn(new Object[]{2L});
            when(subscriptionService.getActivePlanFor(2L)).thenReturn(SubscriptionPlan.FREE);

            assertThatThrownBy(() -> aspect.checkPlan(pjp, method.getAnnotation(RequiresPlan.class)))
                    .isInstanceOf(PlanRequiredException.class)
                    .hasMessageContaining("FREE");

            assertLogged(Level.WARN, "Acceso denegado para usuario 2");
        }

        @Test
        void shouldLogVerificationInfo() throws Throwable {
            Method method = TestService.class.getMethod("premiumOrTrial", Long.class);
            MethodSignature signature = mockSignature(method);
            when(pjp.getSignature()).thenReturn(signature);
            when(pjp.getArgs()).thenReturn(new Object[]{3L});
            when(pjp.proceed()).thenReturn("ok");
            when(subscriptionService.getActivePlanFor(3L)).thenReturn(SubscriptionPlan.PREMIUM_TRIAL);

            aspect.checkPlan(pjp, method.getAnnotation(RequiresPlan.class));

            assertLogged(Level.INFO, "Verificando plan para usuario 3");
        }

    }

    @Nested
    class SubjectResolutionTest {

        @Test
        void shouldResolveUserIdFromLongParam() throws Throwable {
            Method method = TestService.class.getMethod("premiumOnly", Long.class);
            MethodSignature signature = mockSignature(method);
            when(pjp.getSignature()).thenReturn(signature);
            when(pjp.getArgs()).thenReturn(new Object[]{42L});
            when(subscriptionService.getActivePlanFor(42L)).thenReturn(SubscriptionPlan.PREMIUM);
            when(pjp.proceed()).thenReturn("ok");

            aspect.checkPlan(pjp, method.getAnnotation(RequiresPlan.class));

            verify(subscriptionService).getActivePlanFor(42L);
        }

        @Test
        void shouldResolveUserIdFromUserParam() throws Throwable {
            User user = new User();
            user.setId(99L);
            Method method = TestService.class.getMethod("withUserSubject", User.class);
            MethodSignature signature = mockSignature(method);
            when(pjp.getSignature()).thenReturn(signature);
            when(pjp.getArgs()).thenReturn(new Object[]{user});
            when(subscriptionService.getActivePlanFor(99L)).thenReturn(SubscriptionPlan.PREMIUM);
            when(pjp.proceed()).thenReturn("ok");

            aspect.checkPlan(pjp, method.getAnnotation(RequiresPlan.class));

            verify(subscriptionService).getActivePlanFor(99L);
        }

        @Test
        void shouldThrowWhenNoPlanSubjectParam() throws Exception {
            Method method = TestService.class.getMethod("withoutSubject");
            MethodSignature signature = mockSignature(method);
            when(pjp.getSignature()).thenReturn(signature);
            when(pjp.getArgs()).thenReturn(new Object[]{});

            assertThatThrownBy(() -> aspect.checkPlan(pjp, method.getAnnotation(RequiresPlan.class)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ningun parametro @PlanSubject");
        }

        @Test
        void shouldThrowWhenBadSubjectType() throws Exception {
            Method method = TestService.class.getMethod("withBadSubject", String.class);
            MethodSignature signature = mockSignature(method);
            when(pjp.getSignature()).thenReturn(signature);
            when(pjp.getArgs()).thenReturn(new Object[]{"bad"});

            assertThatThrownBy(() -> aspect.checkPlan(pjp, method.getAnnotation(RequiresPlan.class)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("debe ser de tipo User o Long");
        }

    }

    private static MethodSignature mockSignature(Method method) {
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        return signature;
    }

    private void assertLogged(Level level, String message) {
        List<ILoggingEvent> logs = listAppender.list;
        assertThat(logs)
                .anyMatch(e -> e.getLevel().equals(level) && e.getFormattedMessage().contains(message));
    }

    @SuppressWarnings("unused")
    private static class TestService {

        @RequiresPlan(SubscriptionPlan.PREMIUM)
        public void premiumOnly(@PlanSubject Long userId) {}

        @RequiresPlan({SubscriptionPlan.PREMIUM, SubscriptionPlan.PREMIUM_TRIAL})
        public void premiumOrTrial(@PlanSubject Long userId) {}

        @RequiresPlan(SubscriptionPlan.PREMIUM)
        public void withUserSubject(@PlanSubject User user) {}

        @RequiresPlan(SubscriptionPlan.PREMIUM)
        public void withoutSubject() {}

        @RequiresPlan(SubscriptionPlan.PREMIUM)
        public void withBadSubject(@PlanSubject String name) {}

    }

}
