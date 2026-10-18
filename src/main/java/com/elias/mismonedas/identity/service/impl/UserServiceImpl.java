package com.elias.mismonedas.identity.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.identity.domain.enums.UserState;
import com.elias.mismonedas.identity.domain.event.UserDataResetEvent;
import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.identity.domain.event.UserRegisteredEvent;
import com.elias.mismonedas.identity.dto.auth.LoginResponse;
import com.elias.mismonedas.identity.dto.user.UserRequest;
import com.elias.mismonedas.identity.dto.user.UserResponse;
import com.elias.mismonedas.identity.mapper.UserMapper;
import com.elias.mismonedas.identity.repository.UserPreferencesRepository;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.repository.UserSpecifications;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.identity.service.JwtTokenService;
import com.elias.mismonedas.identity.service.UserService;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponseFactory;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService, UserDetailsService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final UserPreferencesRepository preferencesRepository;
    private final SecurityService securityService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public LoginResponse register(UserRequest request) {
        User user = userMapper.toEntity(request);
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already taken");
        }
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        User saved = userRepository.save(user);

        eventPublisher.publishEvent(new UserRegisteredEvent(saved.getId()));

        String token = jwtTokenService.generateToken(saved);
        return new LoginResponse(token);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<UserResponse> findAll() {
        return userRepository.findAll()
                .stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String search, Pageable pageable) {
        Pageable stablePage = PaginationDefaults.stablePage(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> page = userRepository.findAll(UserSpecifications.list(search), stablePage);
        return PageResponseFactory.from(page, userMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return userMapper.toResponse(this.getEntityById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findCurrentUser() {
        return userMapper.toResponse(securityService.getCurrentUser());
    }

    @Override
    @Transactional
    public UserResponse disable(Long id) {
        User user = getEntityById(id);
        ZoneId zoneId = user.getTimeZone().toZoneId();
        user.disable();
        user.setDisabledAt(OffsetDateTime.now(zoneId));
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse enable(Long id) {
        User user = getEntityById(id);
        user.setState(UserState.ENABLED);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
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
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null || email.isBlank()) {
            throw new UsernameNotFoundException("Email is null or empty");
        }
        String normalized = email.trim().toLowerCase();
        return userRepository.findByEmail(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + normalized));
    }

    private User getEntityById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("User id is null");
        }
        return userRepository.findById(id).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public TimeZone[] listTimeZones() {
        return TimeZone.values();
    }

    @Override
    @Transactional
    public void resetAccountData(Long userId) {
        this.deleteUserData(userId);
        eventPublisher.publishEvent(new UserDataResetEvent(userId));
    }

    @Override
    @Transactional
    public void deleteUserAccount(Long userId) {
        this.deleteUserData(userId);
        userRepository.deleteById(userId);
        log.info("Deleted user with ID: {}", userId);
    }

    private void deleteUserData(Long userId) {
        preferencesRepository.deleteByUser_Id(userId);
        eventPublisher.publishEvent(new UserDeletedEvent(userId));
    }
}
