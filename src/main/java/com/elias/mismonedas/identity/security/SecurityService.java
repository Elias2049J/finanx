package com.elias.mismonedas.identity.security;

import com.elias.mismonedas.identity.domain.entity.User;

/** Provides the authenticated user for the current request. */
public interface SecurityService {

    User getCurrentUser();
}
