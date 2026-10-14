package com.elias.mismonedas.notifications.service;

import com.elias.mismonedas.notifications.dto.fcm.FcmTokenResponse;
import com.elias.mismonedas.notifications.dto.fcm.RegisterFcmTokenRequest;

import java.util.List;

public interface FcmTokenService {

    FcmTokenResponse register(Long userId, RegisterFcmTokenRequest request);

    void remove(Long userId, Long tokenId);

    List<FcmTokenResponse> findAllByUser(Long userId);

    List<String> getActiveTokenValues(Long userId);
}
