package com.elias.mismonedas.insights.listener;

import com.elias.mismonedas.insights.service.UserDataContextService;
import com.elias.mismonedas.ledger.domain.event.TransactionRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpException;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserDataContextListener {
    private final UserDataContextService userDataContextService;

    @EventListener
    public void on(TransactionRegisteredEvent event) throws HttpException, IOException {
        userDataContextService.updateUserDataContext(event.userId());
        log.info("User data context updated");
    }
}
