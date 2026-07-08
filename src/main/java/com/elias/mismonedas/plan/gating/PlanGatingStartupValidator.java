package com.elias.mismonedas.plan.gating;

import com.elias.mismonedas.entity.User;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

@Component
public class PlanGatingStartupValidator implements SmartInitializingSingleton {

    private final ConfigurableListableBeanFactory beanFactory;

    public PlanGatingStartupValidator(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> errors = new ArrayList<>();

        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = beanFactory.getBean(beanName);
            } catch (Exception e) {
                continue;
            }

            Class<?> targetClass = AopUtils.getTargetClass(bean);
            if (!targetClass.getName().startsWith("com.elias.mismonedas")) continue;

            ReflectionUtils.doWithMethods(targetClass, method -> {
                RequiresPlan annotation = method.getAnnotation(RequiresPlan.class);
                if (annotation == null) return;

                if (annotation.value() == null || annotation.value().length == 0) {
                    errors.add(describe(method) + ": @RequiresPlan sin ningun plan en value()");
                }

                long subjectCount = countPlanSubjectParams(method);
                if (subjectCount == 0) {
                    errors.add(describe(method) + ": falta @PlanSubject en algun parametro");
                } else if (subjectCount > 1) {
                    errors.add(describe(method) + ": tiene mas de un parametro @PlanSubject (ambiguo)");
                } else {
                    validateSubjectType(method, errors);
                }
            });
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Configuracion invalida de @RequiresPlan detectada en arranque:\n  - "
                    + String.join("\n  - ", errors));
        }
    }

    private long countPlanSubjectParams(Method method) {
        long count = 0;
        for (Parameter p : method.getParameters()) {
            if (p.isAnnotationPresent(PlanSubject.class)) count++;
        }
        return count;
    }

    private void validateSubjectType(Method method, List<String> errors) {
        for (Parameter p : method.getParameters()) {
            if (p.isAnnotationPresent(PlanSubject.class)) {
                Class<?> type = p.getType();
                if (!type.equals(User.class) && !type.equals(Long.class)) {
                    errors.add(describe(method) + ": @PlanSubject debe ser User o Long, es " + type.getSimpleName());
                }
            }
        }
    }

    private String describe(Method method) {
        return method.getDeclaringClass().getSimpleName() + "#" + method.getName();
    }
}
