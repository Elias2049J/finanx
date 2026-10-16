package com.elias.mismonedas.identity.service;

import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.identity.dto.auth.LoginResponse;
import com.elias.mismonedas.identity.dto.user.UserRequest;
import com.elias.mismonedas.identity.dto.user.UserResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {
    LoginResponse register(UserRequest request);

    List<UserResponse> findAll();

    PageResponse<UserResponse> list(String search, Pageable pageable);

    UserResponse findCurrentUser();

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
