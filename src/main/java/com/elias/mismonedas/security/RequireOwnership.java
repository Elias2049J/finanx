package com.elias.mismonedas.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida que el usuario autenticado (JWT subject = email) sea dueño del recurso.
 * El parámetro anotado debe ser el userId del path variable.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireOwnership {
}
