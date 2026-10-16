package com.elias.mismonedas.billing.plan;

import com.elias.mismonedas.billing.domain.enums.SubscriptionPlan;
import com.elias.mismonedas.billing.service.SubscriptionService;
import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RequiresPlanAspect {

    private final SubscriptionService subscriptionService;
    private final SecurityService securityService;

    @Around("@annotation(requiresPlan)")
    public Object checkPlan(ProceedingJoinPoint pjp, RequiresPlan requiresPlan) throws Throwable {
        User user = securityService.getCurrentUser();
        Long userId = user.getId();
        SubscriptionPlan currentPlan = subscriptionService.getActivePlanFor(userId);

        log.info("Verificando plan para usuario {}: actual={}, requerido={}",
                userId, currentPlan, Arrays.toString(requiresPlan.value()));

        boolean allowed = Arrays.asList(requiresPlan.value()).contains(currentPlan);
        if (!allowed) {
            log.warn("Acceso denegado para usuario {}: requiere={}, actual={}",
                    userId, Arrays.toString(requiresPlan.value()), currentPlan);
            throw new PlanRequiredException(requiresPlan.value(), currentPlan);
        }

        log.info("Acceso permitido para usuario {} con plan {}", userId, currentPlan);
        return pjp.proceed();
    }
}
