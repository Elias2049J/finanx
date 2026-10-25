package com.elias.mismonedas.notifications.controller;

import com.elias.mismonedas.notifications.service.impl.NotificationDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationReplayControllerTest {

    private final NotificationDeliveryService deliveryService = mock(NotificationDeliveryService.class);
    private final NotificationReplayController controller = new NotificationReplayController(deliveryService);

    @Test
    void shouldAcceptReplayRequestForScheduledDispatch() {
        var response = controller.replay(91L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(deliveryService).replayFailed(91L);
    }

    @Test
    void shouldRestrictReplayToSystemRole() throws Exception {
        PreAuthorize authorization = NotificationReplayController.class
                .getMethod("replay", Long.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasRole('SYSTEM')");
    }
}
