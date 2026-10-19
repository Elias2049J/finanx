package com.elias.mismonedas.identity.service;

import com.elias.mismonedas.identity.dto.user.UserPreferencesRequest;
import com.elias.mismonedas.identity.dto.user.UserPreferencesResponse;

public interface UserPreferencesService {

    UserPreferencesResponse findByUserId();

    UserPreferencesResponse create(UserPreferencesRequest request);

    UserPreferencesResponse update(UserPreferencesRequest request);
}
