package com.elias.finanx.service;

import com.elias.finanx.dto.fcm.FcmTokenResponse;
import com.elias.finanx.dto.fcm.RegisterFcmTokenRequest;

import java.util.List;

public interface FcmTokenService {

    FcmTokenResponse register(Long userId, RegisterFcmTokenRequest request);

    void remove(Long userId, String token);

    List<FcmTokenResponse> findAllByUser(Long userId);

    List<String> getActiveTokenValues(Long userId);
}
