package com.elias.mismonedas.billing.plan;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
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
            });
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Configuracion invalida de @RequiresPlan detectada en arranque:\n  - "
                    + String.join("\n  - ", errors));
        }
    }

    private String describe(Method method) {
        return method.getDeclaringClass().getSimpleName() + "#" + method.getName();
    }
}
