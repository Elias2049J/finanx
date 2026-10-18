package com.elias.mismonedas.identity.domain.event;

/**
 * Se publica cuando se crea una cuenta nueva.
 *
 * <p>F3: hasta este cambio {@code UserServiceImpl} sembraba las categorias por defecto y
 * suscribia al usuario a PREMIUM llamando directamente a los repositorios y servicios de
 * {@code catalog} y {@code billing}. Cada modo es ahora dueño de su reaccion.
 *
 * <p>Se publica como evento de proceso (Spring), sincrono y dentro de la misma transaccion,
 * por lo que la creacion de la cuenta y sus efectos se confirman o fallan juntos.
 */
public record UserRegisteredEvent(Long userId) {
}
