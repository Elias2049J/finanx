package com.elias.mismonedas.identity.domain.event;

/**
 * Se publica cuando se borra una cuenta: cada módulo borra <b>solo lo suyo</b>.
 *
 * <p>F3/F4: antes {@code UserServiceImpl.deleteUserData()} importaba y llamaba a 9
 * repositorios de {@code catalog}, {@code ledger}, {@code notifications}, {@code planning} y
 * {@code scheduling}, lo que convertia a {@code identity} en el modulo Dios. Con el evento,
 * {@code identity} no conoce ninguna de esas tablas.
 *
 * <p>Se publica como evento de proceso <b>sincrono</b> (no outbox) mientras los modulos
 * vivan en el mismo proceso: asi el borrado sigue siendo atomico y, sobre todo, se respeta
 * el orden — <b>primero</b> los hijos y <b>despues</b> {@code DELETE FROM usuarios}, que de
 * otro modo chocaria con las claves ajenas. Al extraer un modulo, este mismo evento se
 * convierte en uno de RabbitMQ con outbox.
 */
public record UserDeletedEvent(Long userId) {
}
