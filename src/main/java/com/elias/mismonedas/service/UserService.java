package com.elias.mismonedas.service;

import com.elias.mismonedas.dto.auth.LoginResponse;
import com.elias.mismonedas.dto.user.UserRequest;
import com.elias.mismonedas.dto.user.UserResponse;

import com.elias.mismonedas.entity.enums.TimeZone;

import java.util.List;

public interface UserService {
    LoginResponse register(UserRequest request);

    List<UserResponse> findAll();

    UserResponse findById(Long aLong);

    UserResponse disable(Long id);
    UserResponse enable(Long id);
    UserResponse block(Long id);

    UserResponse update(Long aLong, UserRequest request);

    List<UserResponse> searchByName(String q);

    TimeZone[] listTimeZones();

    void resetAccountData(Long userId);

    void deleteUserAccount(Long userId);
}
