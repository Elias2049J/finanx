package com.elias.finanx.service.impl;

import com.elias.finanx.dto.auth.LoginResponse;
import com.elias.finanx.dto.user.UserRequest;
import com.elias.finanx.dto.user.UserResponse;
import com.elias.finanx.entity.enums.TimeZone;
import com.elias.finanx.entity.enums.UserState;
import com.elias.finanx.entity.User;
import com.elias.finanx.mapper.UserMapper;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.UserService;
import com.elias.finanx.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService, UserDetailsService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    @Override
    public LoginResponse register(UserRequest request) {
        User user = userMapper.toEntity(request);
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already taken");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken");
        }

        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        User saved = userRepository.save(user);

        String token = jwtTokenService.generateToken(saved);
        return new LoginResponse(userMapper.toResponse(saved), token);
    }

    @Override
    public List<UserResponse> findAll() {
        return userRepository.findAll()
                .stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    public User getEntityById(Long aLong) {
        return userRepository.findById(aLong).orElseThrow();
    }

    @Override
    public UserResponse findById(Long aLong) {
        return userMapper.toResponse(getEntityById(aLong));
    }

    @Override
    public UserResponse disable(Long id) {
        User user = getEntityById(id);
        user.disable();
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public UserResponse enable(Long id) {
        User user = getEntityById(id);
        user.setState(UserState.ENABLED);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public UserResponse block(Long id) {
        User user = getEntityById(id);
        user.block();
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserResponse update(Long id, UserRequest request) {
        User existing = userRepository.findById(id).orElseThrow();
        userMapper.updateFromDto(request, existing);
        existing.setPassword(passwordEncoder.encode(request.getPassword()));
        return userMapper.toResponse(userRepository.save(existing));
    }

    @Override
    public List<UserResponse> searchByName(String q) {
        return userRepository.findAllByNameContainingIgnoreCase(q)
                .stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String emailClean = email.trim().toLowerCase();
        if (email.isBlank()) {
            throw new UsernameNotFoundException("Bad credentials");
        }
        return userRepository.findByEmail(emailClean)
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));
    }

    @Override
    public TimeZone[] listTimeZones() {
        return TimeZone.values();
    }
}
