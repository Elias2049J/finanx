package com.elias.mismonedas.identity.domain.event;

public record UserLoggedOutEvent(Long userId, String fcmToken) {
}
