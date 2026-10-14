package com.elias.mismonedas.identity.service;

import com.elias.mismonedas.identity.dto.auth.LoginRequest;
import com.elias.mismonedas.identity.dto.auth.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
}

