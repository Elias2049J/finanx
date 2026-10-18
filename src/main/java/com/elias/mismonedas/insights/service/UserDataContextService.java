package com.elias.mismonedas.insights.service;

import com.elias.mismonedas.insights.domain.entity.UserDataContext;
import org.apache.http.HttpException;

import java.io.IOException;

public interface UserDataContextService {
    UserDataContext updateUserDataContext(Long userId) throws HttpException, IOException;

    UserDataContext getUserDataContext(Long userId);
}
