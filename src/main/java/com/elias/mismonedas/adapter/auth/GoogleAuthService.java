package com.elias.mismonedas.adapter.auth;

import com.elias.mismonedas.dto.auth.GoogleIdTokenPayload;
import com.elias.mismonedas.dto.auth.GoogleLoginRequest;
import com.elias.mismonedas.dto.auth.LoginResponse;

public interface GoogleAuthService {
    LoginResponse authenticate(GoogleLoginRequest request);
    LoginResponse login(GoogleIdTokenPayload payload);
    LoginResponse register(GoogleIdTokenPayload payload);
}
