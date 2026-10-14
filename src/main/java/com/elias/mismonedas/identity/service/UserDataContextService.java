package com.elias.mismonedas.identity.service;

import com.elias.mismonedas.identity.domain.entity.UserDataContext;
import org.apache.http.HttpException;

import java.io.IOException;

public interface UserDataContextService {
    UserDataContext updateUserDataContext(Long userId) throws HttpException, IOException;

    UserDataContext getUserDataContext(Long userId);
}
