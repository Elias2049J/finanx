package com.elias.mismonedas.service;

import com.elias.mismonedas.dto.auth.LoginRequest;
import com.elias.mismonedas.dto.auth.LoginResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
}

