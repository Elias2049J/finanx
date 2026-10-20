package com.elias.mismonedas.scheduling.listener;

import com.elias.mismonedas.notifications.domain.enums.NotificationType;
import com.elias.mismonedas.notifications.service.NotificationService;
import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.catalog.repository.CategoryRepository;
import com.elias.mismonedas.planning.domain.entity.Budget;
import com.elias.mismonedas.planning.repository.BudgetRepository;
import com.elias.mismonedas.scheduling.domain.entity.BudgetSchedule;
import com.elias.mismonedas.scheduling.domain.event.BudgetScheduleDueEvent;
import com.elias.mismonedas.scheduling.domain.event.BudgetScheduleExecutedEvent;
import com.elias.mismonedas.scheduling.repository.BudgetScheduleRepository;
import com.elias.mismonedas.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BudgetScheduleListener {

    private final BudgetRepository budgetRepository;
    private final BudgetScheduleRepository bsRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void on(BudgetScheduleDueEvent event) {
        BudgetSchedule schedule = bsRepository.getReferenceById(event.scheduleId());
        Category category = event.categoryId() != null
                ? categoryRepository.getReferenceById(event.categoryId())
                : null;

        Budget budget = budgetRepository.save(Budget.builder()
                .schedule(schedule)
                .user(userRepository.getReferenceById(event.userId()))
                .category(category)
                .alertable(event.alertable())
                .percentAlert(event.percentAlert())
                .limitAmount(event.limitAmount())
                .description(event.description())
                .start(event.start())
                .end(event.end())
                .build());

        notificationService.generate(builder -> builder
                .user(budget.getUser())
                .schedule(schedule)
                .budget(budget)
                .type(NotificationType.INFO)
                .message("Se ha registrado un nuevo presupuesto"));

        eventPublisher.publishEvent(new BudgetScheduleExecutedEvent(
                event.scheduleId(), event.userId(), budget.getId()));
    }
}
