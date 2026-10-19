package com.elias.mismonedas.identity.service;

import com.elias.mismonedas.identity.dto.auth.AuthResponse;
import com.elias.mismonedas.identity.dto.auth.LoginRequest;

public interface AuthService {
    AuthResponse login(LoginRequest request);
}

