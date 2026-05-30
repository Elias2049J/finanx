package com.elias.finanx.service;

import com.elias.finanx.dto.auth.LoginResponse;
import com.elias.finanx.dto.user.UserRequest;
import com.elias.finanx.dto.user.UserResponseDTO;

import com.elias.finanx.entity.User;
import com.elias.finanx.entity.enums.TimeZone;

import java.util.List;

public interface UserService {
    LoginResponse register(UserRequest request);

    List<UserResponseDTO> findAll();

    User getEntityById(Long aLong);

    UserResponseDTO findById(Long aLong);

    UserResponseDTO disable(Long id);
    UserResponseDTO enable(Long id);
    UserResponseDTO block(Long id);

    UserResponseDTO update(Long aLong, UserRequest request);

    List<UserResponseDTO> searchByName(String q);

    TimeZone[] listTimeZones();
}
