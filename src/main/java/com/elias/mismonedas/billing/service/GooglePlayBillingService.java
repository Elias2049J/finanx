package com.elias.mismonedas.billing.service;

import com.elias.mismonedas.billing.dto.googleplay.GooglePlayVerificationRequest;
import com.elias.mismonedas.billing.dto.googleplay.GooglePlayVerificationResponse;

public interface GooglePlayBillingService {

    GooglePlayVerificationResponse verify(GooglePlayVerificationRequest request);
}
