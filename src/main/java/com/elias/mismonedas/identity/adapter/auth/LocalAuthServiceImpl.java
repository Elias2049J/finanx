package com.elias.mismonedas.identity.adapter.auth;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.dto.auth.LoginRequest;
import com.elias.mismonedas.identity.dto.auth.LoginResponse;
import com.elias.mismonedas.identity.dto.user.UserResponse;
import com.elias.mismonedas.identity.mapper.UserMapper;
import com.elias.mismonedas.identity.service.AuthService;
import com.elias.mismonedas.identity.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class LocalAuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtTokenService.generateToken(userDetails);

        UserResponse userDto = null;
        if (userDetails instanceof User u) {
            userDto = userMapper.toResponse(u);
        }

        return new LoginResponse(userDto, token);
    }
}
