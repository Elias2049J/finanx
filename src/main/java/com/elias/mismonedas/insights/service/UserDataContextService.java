package com.elias.mismonedas.insights.service;

import com.elias.mismonedas.insights.domain.entity.UserDataContext;

public interface UserDataContextService {
    UserDataContext updateUserDataContext(Long userId);

    UserDataContext getUserDataContext(Long userId);
}
