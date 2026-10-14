package com.elias.mismonedas.notifications.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.notifications.domain.entity.FcmToken;
import com.elias.mismonedas.notifications.dto.fcm.FcmTokenResponse;
import com.elias.mismonedas.notifications.dto.fcm.RegisterFcmTokenRequest;
import com.elias.mismonedas.notifications.repository.FcmTokenRepository;
import com.elias.mismonedas.notifications.service.FcmTokenService;
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

        // findFirstByUser_IdAndToken evita IncorrectResultSizeDataAccessException
        // cuando existen duplicados legacy (antes del UNIQUE constraint)
        FcmToken existing = fcmTokenRepository.findFirstByUser_IdAndToken(userId, request.getToken())
                .orElse(null);

        if (existing != null) {
            existing.setUpdatedAt(LocalDateTime.now());
            if (request.getDeviceName() != null) {
                existing.setDeviceName(request.getDeviceName());
            }
            fcmTokenRepository.save(existing);

            // Limpiar duplicados legacy si existen
            cleanDuplicates(userId, request.getToken(), existing.getId());

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

    private void cleanDuplicates(Long userId, String token, Long keepId) {
        List<FcmToken> duplicates = fcmTokenRepository.findAllByUser_IdAndToken(userId, token);
        for (FcmToken dup : duplicates) {
            if (!dup.getId().equals(keepId)) {
                fcmTokenRepository.deleteById(dup.getId());
                log.warn("Token FCM duplicado eliminado: id={}, userId={}", dup.getId(), userId);
            }
        }
    }

    @Override
    @Transactional
    public void remove(Long userId, Long tokenId) {
        FcmToken token = fcmTokenRepository.findById(tokenId)
                .orElseThrow(() -> new RuntimeException("Token FCM no encontrado: " + tokenId));
        if (!token.getUser().getId().equals(userId)) {
            log.warn("Intento de eliminar token ajeno: userId={}, tokenId={}, propietario={}",
                    userId, tokenId, token.getUser().getId());
            throw new RuntimeException("El token FCM no pertenece al usuario " + userId);
        }
        fcmTokenRepository.deleteById(tokenId);
        log.info("Token FCM eliminado con id={}", tokenId);
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
