package com.elias.finanx.adapter.auth;

import com.elias.finanx.dto.auth.LoginRequest;
import com.elias.finanx.dto.auth.LoginResponse;
import com.elias.finanx.dto.user.UserRequest;
import com.elias.finanx.dto.user.UserResponseDTO;
import com.elias.finanx.entity.User;
import com.elias.finanx.mapper.UserMapper;
import com.elias.finanx.service.AuthService;
import com.elias.finanx.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class LocalAuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    @Override
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtTokenService.generateToken(userDetails);

        UserResponseDTO userDto = null;
        if (userDetails instanceof User u) {
            userDto = userMapper.toResponse(u);
        }

        return new LoginResponse(userDto, token);
    }
}
