package com.elias.mismonedas.identity.adapter.auth;

import com.elias.mismonedas.identity.dto.auth.AuthResponse;
import com.elias.mismonedas.identity.dto.auth.GoogleIdTokenPayload;
import com.elias.mismonedas.identity.dto.auth.GoogleLoginRequest;

public interface GoogleAuthService {
    AuthResponse authenticate(GoogleLoginRequest request);
    AuthResponse login(GoogleIdTokenPayload payload);
    AuthResponse register(GoogleIdTokenPayload payload);
}
