package com.elias.mismonedas.chat.listener;

import com.elias.mismonedas.chat.repository.ChatMessageRepository;
import com.elias.mismonedas.chat.repository.ChatSessionRepository;
import com.elias.mismonedas.identity.domain.event.UserDeletedEvent;
import com.elias.mismonedas.identity.domain.event.UserDeletionOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatUserDeletionListener {

    private final ChatMessageRepository messageRepository;
    private final ChatSessionRepository sessionRepository;

    @EventListener
    @Order(UserDeletionOrder.CHAT)
    public void onUserDeleted(UserDeletedEvent event) {
        messageRepository.deleteAllBySessionUserId(event.userId());
        sessionRepository.deleteAllByUser_Id(event.userId());
        log.info("Deleted chat sessions and messages for user {}", event.userId());
    }
}
