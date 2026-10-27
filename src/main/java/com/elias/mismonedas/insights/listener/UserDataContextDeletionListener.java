package com.elias.mismonedas.insights.listener;

import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.identity.domain.event.UserDeletionOrder;
import com.elias.mismonedas.insights.repository.UserDataContextRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserDataContextDeletionListener {

    private final UserDataContextRepository repository;

    @EventListener
    @Order(UserDeletionOrder.USER_CONTEXT)
    public void onUserDeleted(UserDeletedEvent event) {
        repository.deleteByUser_Id(event.userId());
    }
}
