package com.elias.mismonedas.identity.service;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.dto.auth.AuthResponse;

public interface AuthTokenService {

    AuthResponse issue(User user);

    AuthResponse refresh(String refreshToken);

    void revoke(String refreshToken);

    void revokeAllForUser(Long userId);
}
