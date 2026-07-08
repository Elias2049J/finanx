# Plan de Implementación — Gating por Plan (Free/Premium) con Spring AOP

**Proyecto:** Finanx — Backend (Java / Spring Boot)
**Alcance:** mecanismo de control de acceso a features premium, extensible sin retrabajo mayor a los ejes de multi-moneda y trial.
**Piloto concreto:** gating del chatbot IA (generación de insights vía Gemini).
**Resolución de sujeto:** parámetro explícito (`@PlanSubject`), no `SecurityContextHolder`.
**Caché:** ninguna (sin TTL). Cada invocación consulta el plan activo.
**Capa de aplicación:** service layer, no controller.

---

## Índice

1. Contexto
2. Objetivo
3. Fuera de alcance
4. Arquitectura de la solución
5. Trampas de AOP y mitigación
6. Piloto: gating del chatbot IA
7. Plan de implementación paso a paso
8. Criterios de aceptación
9. Testing
10. Extensibilidad futura (multi-moneda y trial)
11. Pendiente relacionado — integración con Payments hexagonal

---

## 1. Contexto

Finanx ya tiene el modelo de billing construido: `Subscription` con `SubscriptionPlan`, `SubscriptionState` y `canTransitionTo()`. Falta la pieza que traduce ese estado en una decisión de acceso reusable en el service layer, sin repetir `if (plan == PREMIUM)` disperso en cada método.

La decisión de diseño clave de esta iteración es que **el aspecto no lee el usuario del contexto de seguridad**, sino de un parámetro explícito del método interceptado. Esto es intencional: el chatbot IA, los schedulers de reconciliación y los webhook handlers no siempre corren dentro de un request HTTP autenticado, así que atarse a `SecurityContextHolder` hubiera limitado el gate a controllers. Con parámetro explícito, el mismo mecanismo protege cualquier entry point — HTTP, scheduler, listener de evento — desde el día uno, sin cambios futuros.

## 2. Objetivo

Construir un mecanismo de gating por plan que:

1. Sea declarativo (`@RequiresPlan(...)`) y viva en el service layer, no en el controller.
2. No dependa de `SecurityContextHolder`, para ser invocable desde cualquier origen (HTTP, scheduler, webhook).
3. Falle rápido y de forma ruidosa si un método está mal configurado (sin `@PlanSubject`), en vez de fallar en silencio o con `NullPointerException` en producción.
4. No necesite tocarse cuando se agregue el eje de **trial** — solo se agrega el enum value y se actualizan los `value()` de la anotación donde corresponda.
5. Deje explícitamente fuera de su responsabilidad los **límites numéricos** (ej. "máximo 3 wallets"), que es un problema distinto (no binario) y no debe forzarse dentro de esta misma anotación.

## 3. Fuera de alcance (explícito, para no generar falsas expectativas)

| No cubre esta iteración | Por qué |
|---|---|
| Límites de cantidad (máx. N wallets, máx. N presupuestos) | Es una pregunta cuantitativa ("¿cuántos tiene ya?"), no binaria ("¿tiene el plan?"). Necesita su propio mecanismo (`@RequiresLimit` o resolver explícito), construido *sobre* esta base, no dentro de ella. |
| Soporte de `@RequiresPlan` a nivel de clase (`@within`) | Simplifica el pointcut y, sobre todo, simplifica muchísimo el validador de arranque (sección 4.5). Se puede agregar después sin romper nada existente — es aditivo, no un cambio de diseño. |
| Caché con TTL | Descartado explícitamente por decisión de producto. Cada invocación de un método anotado dispara una consulta a `SubscriptionService.getActivePlanFor()`. Si en el futuro esto pesa en throughput, se ataca aparte, como optimización independiente. |
| Estado `TRIALING` | No existe todavía en `SubscriptionState`/`SubscriptionPlan`. Este plan deja el terreno listo (sección 10) pero no lo implementa. |
| Correlación `WebhookEvent` ↔ `Subscription` vía proveedor externo | Es un problema de la integración hexagonal de payments, no de este mecanismo de gating. Ver sección 11. |

## 4. Arquitectura de la solución

Cinco componentes nuevos, todos en el paquete `com.elias.finanx.plan.gating`:

```
com.elias.finanx.plan.gating/
├── RequiresPlan.java                (anotación de método)
├── PlanSubject.java                  (anotación de parámetro)
├── RequiresPlanAspect.java           (interceptor @Around)
├── PlanRequiredException.java        (excepción de dominio)
├── PlanErrorResponse.java            (DTO de error, con @Schema)
├── PlanGatingStartupValidator.java   (fail-fast en arranque)
└── PlanExceptionHandler.java         (@RestControllerAdvice → 403)
```

### 4.1 `@RequiresPlan`

```java
package com.elias.finanx.plan.gating;

import com.elias.finanx.entity.enums.SubscriptionPlan;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPlan {
    SubscriptionPlan[] value();
}
```

`@Target` limitado a `METHOD` únicamente (no `TYPE`), en línea con la decisión de la sección 3 de no soportar nivel de clase todavía. Esto es lo que simplifica el pointcut y el validador de arranque.

### 4.2 `@PlanSubject`

```java
package com.elias.finanx.plan.gating;

import java.lang.annotation.*;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface PlanSubject {
}
```

Marca **cuál** parámetro del método contiene la identidad del usuario a evaluar. Tipos soportados por el aspecto: `User` (se extrae `.getId()`) o `Long` (se usa directo como `userId`). Cualquier otro tipo en un parámetro marcado con `@PlanSubject` es un error de configuración detectado en arranque (sección 4.5).

Uso:

```java
@RequiresPlan(SubscriptionPlan.PREMIUM)
public InsightResponse generateInsight(@PlanSubject User user, InsightRequest request) {
    ...
}
```

### 4.3 `RequiresPlanAspect`

```java
package com.elias.finanx.plan.gating;

import com.elias.finanx.entity.User;
import com.elias.finanx.entity.enums.SubscriptionPlan;
import com.elias.finanx.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

@Aspect
@Component
@RequiredArgsConstructor
public class RequiresPlanAspect {

    private final SubscriptionService subscriptionService;

    @Around("@annotation(requiresPlan)")
    public Object checkPlan(ProceedingJoinPoint pjp, RequiresPlan requiresPlan) throws Throwable {
        Long userId = resolveSubjectUserId(pjp);
        SubscriptionPlan currentPlan = subscriptionService.getActivePlanFor(userId);

        boolean allowed = Arrays.asList(requiresPlan.value()).contains(currentPlan);
        if (!allowed) {
            throw new PlanRequiredException(requiresPlan.value(), currentPlan);
        }
        return pjp.proceed();
    }

    private Long resolveSubjectUserId(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = pjp.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            if (hasPlanSubject(parameters[i])) {
                return extractUserId(args[i], method);
            }
        }

        // Red de seguridad en tiempo de ejecución. El validador de arranque
        // (PlanGatingStartupValidator) debería impedir llegar aquí, pero
        // esta excepción queda como defensa en profundidad.
        throw new IllegalStateException(
            "Método " + method.getDeclaringClass().getSimpleName() + "#" + method.getName()
            + " está anotado con @RequiresPlan pero no tiene ningún parámetro @PlanSubject.");
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
```

**Por qué no usa `@within`:** el pointcut es solo `@annotation(requiresPlan)`, es decir, exclusivamente métodos anotados directamente. Esto evita la ambigüedad de "¿el método hace override al nivel de clase?" y reduce la superficie para trampas de AOP (menos casos → menos formas de fallar en silencio).

### 4.4 `PlanRequiredException`, `PlanErrorResponse` y handler

```java
package com.elias.finanx.plan.gating;

import com.elias.finanx.entity.enums.SubscriptionPlan;
import lombok.Getter;

@Getter
public class PlanRequiredException extends RuntimeException {
    private final SubscriptionPlan[] required;
    private final SubscriptionPlan current;

    public PlanRequiredException(SubscriptionPlan[] required, SubscriptionPlan current) {
        super("Requiere plan " + java.util.Arrays.toString(required) + ", actual: " + current);
        this.required = required;
        this.current = current;
    }
}
```

```java
package com.elias.finanx.plan.gating;

import com.elias.finanx.entity.enums.SubscriptionPlan;
import io.swagger.v3.oas.annotations.media.Schema;

public record PlanErrorResponse(
    @Schema(description = "Código de error estable para el cliente") String code,
    @Schema(description = "Mensaje legible para debugging") String message,
    @Schema(description = "Planes que hubieran permitido la operación") SubscriptionPlan[] required,
    @Schema(description = "Plan actual del usuario") SubscriptionPlan current
) {}
```

```java
package com.elias.finanx.plan.gating;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PlanExceptionHandler {

    @ExceptionHandler(PlanRequiredException.class)
    public ResponseEntity<PlanErrorResponse> handle(PlanRequiredException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new PlanErrorResponse("PLAN_REQUIRED", ex.getMessage(), ex.getRequired(), ex.getCurrent()));
    }
}
```

### 4.5 `PlanGatingStartupValidator` — el corazón del "cuidado con las trampas de AOP"

Este componente evita el peor escenario de AOP: **un método mal configurado que compila, no lanza error, y simplemente nunca se protege**. En vez de descubrirlo en producción cuando un usuario Free accede a una feature premium, la app **no debe arrancar**.

```java
package com.elias.finanx.plan.gating;

import com.elias.finanx.entity.User;
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
                continue; // beans con creación perezosa/condicional, se ignoran
            }

            Class<?> targetClass = AopUtils.getTargetClass(bean);
            if (!targetClass.getName().startsWith("com.elias.finanx")) continue;

            ReflectionUtils.doWithMethods(targetClass, method -> {
                RequiresPlan annotation = method.getAnnotation(RequiresPlan.class);
                if (annotation == null) return;

                if (annotation.value() == null || annotation.value().length == 0) {
                    errors.add(describe(method) + ": @RequiresPlan sin ningún plan en value()");
                }

                long subjectCount = countPlanSubjectParams(method);
                if (subjectCount == 0) {
                    errors.add(describe(method) + ": falta @PlanSubject en algún parámetro");
                } else if (subjectCount > 1) {
                    errors.add(describe(method) + ": tiene más de un parámetro @PlanSubject (ambiguo)");
                } else {
                    validateSubjectType(method, errors);
                }
            });
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                "Configuración inválida de @RequiresPlan detectada en arranque:\n  - "
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
```

Con esto, si mañana agregas `@RequiresPlan(PREMIUM)` a un método y te olvidas del `@PlanSubject`, **la aplicación no levanta** — falla en el arranque local o en CI, no en producción con un usuario real.

## 5. Trampas de AOP y cómo este diseño las mitiga

| Trampa | Mitigación en este diseño |
|---|---|
| Self-invocation (`this.metodoAnotado()` desde dentro de la misma clase no dispara el aspecto) | **No hay mitigación automática posible** — es una limitación estructural de los proxies de Spring AOP. Se documenta como regla de código: si un método anotado necesita invocarse desde otro método del mismo service, extraer a un bean colaborador separado. Documentado en el README del módulo (paso 7 del plan). |
| Métodos `private` o no-públicos no se interceptan | El validador de arranque escanea con `ReflectionUtils.doWithMethods`, que sí ve métodos `private` — pero como buena práctica, `@RequiresPlan` solo debe usarse en métodos `public` de beans Spring. Se agrega como convención explícita, no como chequeo automático (Spring AOP directamente ignora `private` sin avisar). |
| Bean no gestionado por Spring (`new EsteService()` en vez de inyección) | Fuera del alcance de un validador automático; se mitiga por convención — todo servicio de negocio ya es `@Service` en el codebase actual. |
| Ambigüedad de qué parámetro es el sujeto cuando hay varios `Long` | Resuelta explícitamente por la anotación `@PlanSubject` en vez de heurística por tipo/nombre — y el validador rechaza más de una ocurrencia. |
| Fallo silencioso por configuración incompleta | Resuelto por `PlanGatingStartupValidator`: falla el arranque, no la producción. |
| Orden de aspectos si se agrega otro (`@Transactional` custom, rate limiting) | No aplica todavía (un solo aspecto), pero se deja documentado: si se agrega otro `@Around`, el de plan debe correr con `@Order` más bajo (antes) para no abrir transacciones/recursos en requests que de todos modos se van a rechazar. |

## 6. Piloto: gating del chatbot IA

Estructura actual: un service que orquesta `InsightGeminiAdapter` para generar el análisis. El cambio es de dos líneas en el service, más el ajuste del controller para pasar el usuario explícitamente.

**Antes:**

```java
public InsightResponse generateInsight(User user, InsightRequest request) {
    // llamada a InsightGeminiAdapter...
}
```

**Después:**

```java
@RequiresPlan(SubscriptionPlan.PREMIUM)
public InsightResponse generateInsight(@PlanSubject User user, InsightRequest request) {
    // llamada a InsightGeminiAdapter, sin cambios internos
}
```

**Controller** — responsable de resolver el `User` autenticado y pasarlo explícitamente (aquí sí es razonable usar `@AuthenticationPrincipal`, porque el controller es la frontera HTTP; el aspecto sigue sin saber nada de `SecurityContext`):

```java
@PostMapping("/insights")
public ResponseEntity<InsightResponse> generate(
        @AuthenticationPrincipal User user,
        @RequestBody InsightRequest request) {
    return ResponseEntity.ok(insightService.generateInsight(user, request));
}
```

Si el scheduler o algún job interno también dispara insights (por ejemplo, generación automática mensual), ese caller simplemente pasa el `User`/`userId` que ya tiene en mano desde su propia consulta — sin tocar `SecurityContextHolder`, sin diferencias de comportamiento entre origen HTTP y origen batch.

## 7. Plan de implementación paso a paso

| # | Tarea | Depende de |
|---|---|---|
| 0 | Verificar/crear `SubscriptionService.getActivePlanFor(Long userId)`. Regla de negocio a fijar: si el usuario no tiene ninguna `Subscription`, se retorna `SubscriptionPlan.FREE` por defecto (no excepción, no null). | — |
| 1 | Crear `@RequiresPlan` y `@PlanSubject` en `com.elias.finanx.plan.gating`. | 0 |
| 2 | Crear `PlanRequiredException`, `PlanErrorResponse` y `PlanExceptionHandler`. | 1 |
| 3 | Implementar `RequiresPlanAspect`. | 1, 2 |
| 4 | Implementar `PlanGatingStartupValidator`. | 1 |
| 5 | Aplicar `@RequiresPlan` + `@PlanSubject` al método piloto (`InsightService.generateInsight`), ajustar controller. | 3, 4 |
| 6 | Suite de tests (sección 9). | 5 |
| 7 | README corto del módulo (`plan/gating/README.md`): convención de uso, límite de self-invocation, nota de extensibilidad (sección 10). | 6 |

Cada tarea es su propio PR razonable — ninguna depende de terminar completamente la anterior salvo lo indicado en la columna "Depende de".

## 8. Criterios de aceptación

**Tarea 0 — `getActivePlanFor`:**
- [ ] Dado un usuario con `Subscription` en estado `ACTIVE`, retorna el `plan` de esa suscripción.
- [ ] Dado un usuario sin ninguna fila en `suscripciones`, retorna `FREE` sin lanzar excepción.
- [ ] Dado un usuario con `Subscription` en estado `CANCELED`/`EXPIRED`, retorna `FREE` (no el plan de la suscripción cancelada).

**Tareas 1–4 — infraestructura de gating:**
- [ ] Un método anotado con `@RequiresPlan(PREMIUM)` y `@PlanSubject User user` permite la ejecución cuando `getActivePlanFor` retorna `PREMIUM`.
- [ ] El mismo método lanza `PlanRequiredException` cuando el plan es `FREE`.
- [ ] La excepción se traduce a HTTP 403 con body `{"code":"PLAN_REQUIRED", "required":[...], "current":"FREE"}`.
- [ ] Un método anotado con `@RequiresPlan` sin `@PlanSubject` en ningún parámetro **impide el arranque de la aplicación**, con mensaje de error que identifica la clase y método exactos.
- [ ] Un método con dos parámetros `@PlanSubject` también impide el arranque (ambigüedad detectada).
- [ ] Un `@PlanSubject` sobre un tipo distinto de `User`/`Long` impide el arranque.

**Tarea 5 — piloto:**
- [ ] Un usuario `FREE` que llama al endpoint de insights recibe 403 con `PLAN_REQUIRED`.
- [ ] Un usuario `PREMIUM` recibe 200 con el insight generado, sin cambios de comportamiento respecto a hoy.
- [ ] El scheduler/job que dispare insights internamente (si existe) sigue funcionando pasando el `User` que ya tiene resuelto, sin depender de contexto HTTP.

## 9. Testing

### 9.1 Unit test del aspecto (sin contexto Spring)

Verifica la lógica pura: extracción del sujeto, decisión de permitir/denegar. Se mockea `ProceedingJoinPoint` y `SubscriptionService`.

```java
class RequiresPlanAspectTest {

    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final RequiresPlanAspect aspect = new RequiresPlanAspect(subscriptionService);

    @Test
    void permiteEjecucionCuandoElPlanCoincide() throws Throwable {
        User user = User.builder().id(1L).build();
        Object[] args = {user, "otroArg"};
        ProceedingJoinPoint pjp = mockJoinPointFor(SampleService.class, "gatedMethod", args);

        when(subscriptionService.getActivePlanFor(1L)).thenReturn(SubscriptionPlan.PREMIUM);

        RequiresPlan requiresPlan = SampleService.class
            .getMethod("gatedMethod", User.class, String.class)
            .getAnnotation(RequiresPlan.class);

        aspect.checkPlan(pjp, requiresPlan);

        verify(pjp).proceed();
    }

    @Test
    void lanzaExcepcionCuandoElPlanNoAlcanza() throws Throwable {
        User user = User.builder().id(1L).build();
        ProceedingJoinPoint pjp = mockJoinPointFor(SampleService.class, "gatedMethod", new Object[]{user, "x"});
        when(subscriptionService.getActivePlanFor(1L)).thenReturn(SubscriptionPlan.FREE);

        RequiresPlan requiresPlan = SampleService.class
            .getMethod("gatedMethod", User.class, String.class)
            .getAnnotation(RequiresPlan.class);

        assertThrows(PlanRequiredException.class, () -> aspect.checkPlan(pjp, requiresPlan));
        verify(pjp, never()).proceed();
    }

    // clase de apoyo solo para el test — no forma parte del código de producción
    static class SampleService {
        @RequiresPlan(SubscriptionPlan.PREMIUM)
        public void gatedMethod(@PlanSubject User user, String other) {}
    }
}
```

### 9.2 Integration test — prueba que el proxy de Spring realmente intercepta

**Este es el test que de verdad importa contra las trampas de AOP.** El test unitario de 9.1 prueba la lógica del aspecto llamándolo directamente — eso no prueba que Spring efectivamente esté envolviendo el bean en un proxy y aplicando el aspecto en runtime. Es exactamente el tipo de falso positivo que hace que un bug de AOP pase desapercibido.

```java
@SpringBootTest(classes = {RequiresPlanAspect.class, PlanGatingStartupValidator.class,
                           IntegrationTestConfig.SampleGatedService.class})
@EnableAspectJAutoProxy
class RequiresPlanAspectIntegrationTest {

    @Autowired
    private IntegrationTestConfig.SampleGatedService sampleService;

    @MockBean
    private SubscriptionService subscriptionService;

    @Test
    void elProxyDeSpringInterceptaLaLlamadaReal() {
        when(subscriptionService.getActivePlanFor(1L)).thenReturn(SubscriptionPlan.FREE);
        User user = User.builder().id(1L).build();

        assertThrows(PlanRequiredException.class,
            () -> sampleService.gatedMethod(user));
    }
}
```

### 9.3 Test del validador de arranque (fail-fast)

```java
@Test
void elContextoNoArrancaSiFaltaPlanSubject() {
    assertThrows(Exception.class, () -> {
        new AnnotationConfigApplicationContext(MisconfiguredBeanConfig.class);
    });
    // el mensaje de la excepción debe contener el nombre de clase/método ofensor
}
```

Clase de apoyo con el error deliberado:

```java
static class MisconfiguredService {
    @RequiresPlan(SubscriptionPlan.PREMIUM)
    public void metodoSinSubject(String algo) {} // falta @PlanSubject
}
```

### 9.4 Test end-to-end del piloto (MockMvc)

```java
@Test
void usuarioFreePideInsightYRecibe403() throws Exception {
    mockMvc.perform(post("/api/insights")
            .with(user(freeUserPrincipal))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PLAN_REQUIRED"));
}

@Test
void usuarioPremiumPideInsightYRecibe200() throws Exception {
    mockMvc.perform(post("/api/insights")
            .with(user(premiumUserPrincipal))
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isOk());
}
```

### 9.5 Nota sobre self-invocation

No se escribe un test que "pruebe" que la self-invocation bypassa el gate — hacerlo normalizaría el patrón. En su lugar, queda documentado en el README del módulo (tarea 7) como regla de diseño a respetar en code review.

## 10. Extensibilidad futura (multi-moneda y trial)

**Trial:** cuando se implemente, los únicos cambios son:
1. Agregar `TRIALING` a `SubscriptionPlan` (o a `SubscriptionState`, según cómo se termine modelando).
2. Actualizar `SubscriptionService.getActivePlanFor()` para derivar `TRIALING` si corresponde (por ejemplo, consultando `Subscription.isInTrial()`).
3. Actualizar los `value()` de `@RequiresPlan` donde el trial deba dar acceso premium: `@RequiresPlan({PREMIUM, TRIALING})`.

Cero cambios en `RequiresPlanAspect`, `@PlanSubject`, `PlanRequiredException`, ni `PlanGatingStartupValidator`.

**Multi-moneda:** este mecanismo no se toca. Los límites de cantidad (máx. wallets por plan) necesitan su propio componente porque la pregunta no es binaria — se construye como una pieza *adicional* que reutiliza `SubscriptionService.getActivePlanFor()`, no una modificación de `@RequiresPlan`.

## 11. Pendiente relacionado — integración con Payments hexagonal

Este documento **no cubre** el flujo `WebhookEvent → PaymentPort → Subscription.transitionTo()`. Ese es un problema separado, con su propio hueco identificado en el modelo actual de `Subscription`: falta un campo de correlación con el proveedor externo.

Campos a agregar a `Subscription` para cerrar ese hexágono:

```java
@Enumerated(EnumType.STRING)
@Column(name = "proveedor", nullable = false)
private PaymentProvider provider;

@Column(name = "id_suscripcion_externa", unique = true)
private String externalSubscriptionId;
```

Con esto, un `SubscriptionWebhookHandler` puede resolver `subscriptionRepository.findByExternalSubscriptionId(id)` como primer paso de cualquier evento de suscripción entrante (`customer.subscription.updated`, `invoice.payment_failed`, etc.), sin depender de navegar desde `Payment.externalReference` hacia atrás.

Este trabajo queda fuera de este documento y debería ser su propio plan, con su propia sección de criterios de aceptación y testing — en particular cubriendo idempotencia (un mismo `externalEventId` no debe disparar dos transiciones de estado) y el caso de eventos que llegan fuera de orden.

---

*Documento generado para Finanx — módulo de gating por plan (Opción B). Versión sin trial, sin multi-moneda, sin caché TTL, gating a nivel de service layer con resolución de sujeto por parámetro explícito.*
