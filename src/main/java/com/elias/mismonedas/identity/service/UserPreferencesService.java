package com.elias.mismonedas.identity.service;

import com.elias.mismonedas.identity.dto.user.UserPreferencesRequest;
import com.elias.mismonedas.identity.dto.user.UserPreferencesResponse;

public interface UserPreferencesService {

    UserPreferencesResponse findByUserId(Long userId);

    UserPreferencesResponse getOrCreateDefaults(Long userId);

    UserPreferencesResponse create(Long userId, UserPreferencesRequest request);

    UserPreferencesResponse update(Long userId, UserPreferencesRequest request);
}
