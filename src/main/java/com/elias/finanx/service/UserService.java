package com.elias.finanx.service;

import com.elias.finanx.dto.auth.LoginResponse;
import com.elias.finanx.dto.user.UserRequest;
import com.elias.finanx.dto.user.UserResponse;

import com.elias.finanx.entity.enums.TimeZone;

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
}
