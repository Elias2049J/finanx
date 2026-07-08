package com.elias.finanx.service;

import com.elias.finanx.dto.user.UserPreferencesRequest;
import com.elias.finanx.dto.user.UserPreferencesResponse;

public interface UserPreferencesService {

    UserPreferencesResponse findByUserId(Long userId);

    UserPreferencesResponse getOrCreateDefaults(Long userId);

    UserPreferencesResponse create(Long userId, UserPreferencesRequest request);

    UserPreferencesResponse update(Long userId, UserPreferencesRequest request);
}
