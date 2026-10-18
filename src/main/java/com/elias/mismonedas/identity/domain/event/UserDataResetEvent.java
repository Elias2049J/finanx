package com.elias.mismonedas.identity.domain.event;

/**
 * Se publica cuando un usuario reinicia sus datos y vuelve a empezar de cero, conservando la
 * cuenta: se borra todo y se vuelven a sembrar las categorias por defecto.
 *
 * <p>Es distinto de {@link UserRegisteredEvent} porque no debe re-suscribir al usuario.
 */
public record UserDataResetEvent(Long userId) {
}
