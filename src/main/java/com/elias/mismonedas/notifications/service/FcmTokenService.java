package com.elias.mismonedas.notifications.service;

import com.elias.mismonedas.notifications.dto.fcm.FcmTokenResponse;
import com.elias.mismonedas.notifications.dto.fcm.RegisterFcmTokenRequest;
import com.elias.mismonedas.shared.dto.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FcmTokenService {

    FcmTokenResponse register(RegisterFcmTokenRequest request);

    void remove(Long tokenId);

    List<FcmTokenResponse> findAllByUser(Long userId);

    PageResponse<FcmTokenResponse> list(Pageable pageable);

    List<String> getActiveTokenValues(Long userId);
}
