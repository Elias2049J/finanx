package com.elias.mismonedas.service;

import com.elias.mismonedas.entity.UserDataContext;
import org.apache.http.HttpException;

import java.io.IOException;

public interface UserDataContextService {
    UserDataContext updateUserDataContext(Long userId) throws HttpException, IOException;

    UserDataContext getUserDataContext(Long userId);
}
