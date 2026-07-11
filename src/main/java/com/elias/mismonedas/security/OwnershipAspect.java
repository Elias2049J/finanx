package com.elias.mismonedas.security;

import com.elias.mismonedas.entity.User;
import com.elias.mismonedas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OwnershipAspect {

    private final UserRepository userRepository;

    @Around("@annotation(org.springframework.web.bind.annotation.GetMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PostMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.PutMapping) || " +
            "@annotation(org.springframework.web.bind.annotation.DeleteMapping)")
    public Object checkOwnership(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = pjp.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (hasRequireOwnership(parameters[i]) && args[i] instanceof Long userId) {
                validateOwnership(userId);
            }
        }

        return pjp.proceed();
    }

    private void validateOwnership(Long pathUserId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new SecurityException("No authenticated");
        }

        String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new SecurityException("User not found"));

        if (!user.getId().equals(pathUserId)) {
            log.warn("Ownership violation: user {} tried to access resources of user {}",
                    user.getId(), pathUserId);
            throw new SecurityException("Access denied: you cannot access another user's resources");
        }
    }

    private boolean hasRequireOwnership(Parameter parameter) {
        return parameter.isAnnotationPresent(RequireOwnership.class);
    }
}
