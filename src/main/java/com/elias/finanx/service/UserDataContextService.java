package com.elias.finanx.service;

import com.elias.finanx.entity.UserDataContext;
import org.apache.http.HttpException;

import java.io.IOException;

public interface UserDataContextService {
    UserDataContext updateUserDataContext(Long userId) throws HttpException, IOException;

    UserDataContext getUserDataContext(Long userId);
}
