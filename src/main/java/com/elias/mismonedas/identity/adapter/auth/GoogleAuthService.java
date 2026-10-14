package com.elias.mismonedas.identity.adapter.auth;

import com.elias.mismonedas.identity.dto.auth.GoogleIdTokenPayload;
import com.elias.mismonedas.identity.dto.auth.GoogleLoginRequest;
import com.elias.mismonedas.identity.dto.auth.LoginResponse;

public interface GoogleAuthService {
    LoginResponse authenticate(GoogleLoginRequest request);
    LoginResponse login(GoogleIdTokenPayload payload);
    LoginResponse register(GoogleIdTokenPayload payload);
}
