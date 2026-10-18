package com.elias.mismonedas.identity.security;

import com.elias.mismonedas.identity.domain.entity.User;

public interface SecurityService {

    User getCurrentUser();
}
