package com.elias.mismonedas.identity.domain.entity;

import com.elias.mismonedas.identity.domain.enums.UserState;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountDeletionAuthenticationTest {

    @Test
    void pendingDeletionIsEnabledForReauthenticationBeforeDeadline() {
        OffsetDateTime now = OffsetDateTime.now();
        User user = pendingDeletion(now.plusMinutes(1));

        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void pendingDeletionIsNotEnabledAtOrAfterDeadline() {
        User user = pendingDeletion(OffsetDateTime.now().minusMinutes(1));

        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    void cancellationIsRejectedAtTheExactDeadline() {
        OffsetDateTime deadline = OffsetDateTime.now();
        User user = pendingDeletion(deadline);

        assertThat(user.canCancelDeletionAt(deadline.minusNanos(1))).isTrue();
        assertThat(user.canCancelDeletionAt(deadline)).isFalse();
    }

    @Test
    void ordinaryDisabledAccountRemainsDisabled() {
        User user = new User();
        user.setState(UserState.DISABLED);

        assertThat(user.isEnabled()).isFalse();
    }

    @Test
    void blockedAccountRemainsBlocked() {
        User user = pendingDeletion(OffsetDateTime.now().plusMinutes(1));
        user.setState(UserState.BLOCKED);

        assertThat(user.isEnabled()).isFalse();
    }

    private User pendingDeletion(OffsetDateTime deadline) {
        User user = new User();
        user.setState(UserState.ENABLED);
        user.requestDeletion(deadline.minusDays(7), deadline);
        return user;
    }
}
