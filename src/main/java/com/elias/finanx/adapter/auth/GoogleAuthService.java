package com.elias.finanx.adapter.auth;

import com.elias.finanx.dto.auth.GoogleIdTokenPayload;
import com.elias.finanx.dto.auth.GoogleLoginRequest;
import com.elias.finanx.dto.auth.LoginResponse;

public interface GoogleAuthService {
    LoginResponse authenticate(GoogleLoginRequest request);
    LoginResponse login(GoogleIdTokenPayload payload);
    LoginResponse register(GoogleIdTokenPayload payload);
}
