package com.elias.finanx.service.impl;

import com.elias.finanx.dto.fcm.FcmTokenResponse;
import com.elias.finanx.dto.fcm.RegisterFcmTokenRequest;
import com.elias.finanx.entity.FcmToken;
import com.elias.finanx.entity.User;
import com.elias.finanx.repository.FcmTokenRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.FcmTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmTokenServiceImpl implements FcmTokenService {

    private final FcmTokenRepository fcmTokenRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public FcmTokenResponse register(Long userId, RegisterFcmTokenRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + userId));

        FcmToken existing = fcmTokenRepository.findByUserIdAndToken(userId, request.getToken())
                .orElse(null);

        if (existing != null) {
            existing.setUpdatedAt(LocalDateTime.now());
            if (request.getDeviceName() != null) {
                existing.setDeviceName(request.getDeviceName());
            }
            fcmTokenRepository.save(existing);
            return toResponse(existing);
        }

        FcmToken fcmToken = FcmToken.builder()
                .user(user)
                .token(request.getToken())
                .deviceName(request.getDeviceName())
                .build();

        fcmToken = fcmTokenRepository.save(fcmToken);
        log.info("Token FCM registrado para userId={}, deviceName={}", userId, request.getDeviceName());
        return toResponse(fcmToken);
    }

    @Override
    @Transactional
    public void remove(Long userId, String token) {
        fcmTokenRepository.deleteByUserIdAndToken(userId, token);
        log.info("Token FCM eliminado para userId={}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FcmTokenResponse> findAllByUser(Long userId) {
        return fcmTokenRepository.findAllByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getActiveTokenValues(Long userId) {
        return fcmTokenRepository.findAllByUserId(userId)
                .stream()
                .map(FcmToken::getToken)
                .toList();
    }

    private FcmTokenResponse toResponse(FcmToken entity) {
        return FcmTokenResponse.builder()
                .id(entity.getId())
                .token(entity.getToken())
                .deviceName(entity.getDeviceName())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
