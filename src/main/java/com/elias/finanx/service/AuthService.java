package com.elias.finanx.service;

import com.elias.finanx.dto.auth.LoginRequest;
import com.elias.finanx.dto.auth.LoginResponse;
import com.elias.finanx.dto.user.UserRequest;

public interface AuthService {
    LoginResponse login(LoginRequest request);
}

