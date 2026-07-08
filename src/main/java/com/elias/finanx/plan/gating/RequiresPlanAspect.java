package com.elias.finanx.plan.gating;

import com.elias.finanx.entity.User;
import com.elias.finanx.entity.enums.SubscriptionPlan;
import com.elias.finanx.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RequiresPlanAspect {

    private final SubscriptionService subscriptionService;

    @Around("@annotation(requiresPlan)")
    public Object checkPlan(ProceedingJoinPoint pjp, RequiresPlan requiresPlan) throws Throwable {
        Long userId = resolveSubjectUserId(pjp);
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

    private Long resolveSubjectUserId(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = pjp.getArgs();

        log.debug("Resolviendo @PlanSubject en {}.{}",
                method.getDeclaringClass().getSimpleName(), method.getName());

        for (int i = 0; i < parameters.length; i++) {
            if (hasPlanSubject(parameters[i])) {
                return extractUserId(args[i], method);
            }
        }

        throw new IllegalStateException(
                "Metodo " + method.getDeclaringClass().getSimpleName() + "#" + method.getName()
                + " esta anotado con @RequiresPlan pero no tiene ningun parametro @PlanSubject.");
    }

    private boolean hasPlanSubject(Parameter parameter) {
        for (Annotation ann : parameter.getAnnotations()) {
            if (ann.annotationType().equals(PlanSubject.class)) return true;
        }
        return false;
    }

    private Long extractUserId(Object arg, Method method) {
        if (arg instanceof User user) return user.getId();
        if (arg instanceof Long id) return id;
        throw new IllegalStateException(
                "@PlanSubject en " + method.getDeclaringClass().getSimpleName() + "#" + method.getName()
                + " debe ser de tipo User o Long, no " + (arg == null ? "null" : arg.getClass()));
    }
}
