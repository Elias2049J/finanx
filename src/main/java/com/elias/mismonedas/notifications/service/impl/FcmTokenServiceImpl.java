package com.elias.mismonedas.notifications.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.notifications.domain.entity.FcmToken;
import com.elias.mismonedas.notifications.dto.fcm.FcmTokenResponse;
import com.elias.mismonedas.notifications.dto.fcm.RegisterFcmTokenRequest;
import com.elias.mismonedas.notifications.repository.FcmTokenRepository;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import com.elias.mismonedas.shared.dto.pagination.PageResponseFactory;
import com.elias.mismonedas.shared.dto.pagination.PaginationDefaults;
import com.elias.mismonedas.notifications.service.FcmTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmTokenServiceImpl implements FcmTokenService {

    private final FcmTokenRepository fcmTokenRepository;
    private final SecurityService securityService;

    @Override
    @Transactional
    public FcmTokenResponse register(RegisterFcmTokenRequest request) {
        User user = securityService.getCurrentUser();
        Long userId = user.getId();
        fcmTokenRepository.upsertByToken(userId, request.getToken(), request.getDeviceName(), LocalDateTime.now());
        FcmToken fcmToken = fcmTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new IllegalStateException("FCM token disappeared after registration"));
        log.info("Token FCM registrado o transferido para userId={}, deviceName={}", userId, request.getDeviceName());
        return toResponse(fcmToken);
    }

    @Override
    @Transactional
    public void remove(Long tokenId) {
        Long userId = securityService.getCurrentUser().getId();

        int deleted = fcmTokenRepository.deleteByIdAndUserId(tokenId, userId);
        if (deleted > 0) {
            log.info("Token FCM eliminado con id={}", tokenId);
            return;
        }

        boolean belongsToAnotherUser = fcmTokenRepository.findById(tokenId)
                .map(token -> !token.getUser().getId().equals(userId))
                .orElse(false);
        if (belongsToAnotherUser) {
            log.warn("Intento de eliminar token ajeno: userId={}, tokenId={}", userId, tokenId);
            throw new AccessDeniedException("El token FCM no pertenece al usuario " + userId);
        }
        log.info("Token FCM inexistente, eliminacion idempotente id={}", tokenId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FcmTokenResponse> findAllByUser(Long userId) {
        return fcmTokenRepository.findAllByUser_Id(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FcmTokenResponse> list(Pageable pageable) {
        Long userId = securityService.getCurrentUser().getId();
        Pageable stablePage = PaginationDefaults.stablePage(pageable, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponseFactory.from(fcmTokenRepository.findAllByUser_Id(userId, stablePage), this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getActiveTokenValues(Long userId) {
        return fcmTokenRepository.findAllByUser_Id(userId)
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
