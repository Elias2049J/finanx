package com.elias.mismonedas.insights.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.insights.domain.entity.UserDataContext;
import com.elias.mismonedas.insights.repository.UserDataContextRepository;
import com.elias.mismonedas.insights.service.UserDataContextService;
import com.elias.mismonedas.insights.service.analytics.AnalyticsService;
import com.elias.mismonedas.shared.domain.vo.DateRange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class UserDataContextServiceImpl implements UserDataContextService {
    private final UserDataContextRepository userDataContextRepository;
    private final UserRepository userRepository;
    private final AnalyticsService analyticsService;

    public UserDataContextServiceImpl(UserDataContextRepository userDataContextRepository,
                                      UserRepository userRepository,
                                      @Qualifier("transactionAnalyticsService")
                                      AnalyticsService analyticsService) {
        this.userDataContextRepository = userDataContextRepository;
        this.userRepository = userRepository;
        this.analyticsService = analyticsService;
    }

    @Override
    public UserDataContext updateUserDataContext(Long userId) {
        LocalDate currentDate = LocalDate.now();
        LocalDate previousMonth = currentDate.minusMonths(1);

        DateRange currentPeriod = new DateRange(currentDate.withDayOfMonth(1), currentDate);
        DateRange previousPeriod = new DateRange(
                previousMonth.withDayOfMonth(1),
                previousMonth.withDayOfMonth(previousMonth.lengthOfMonth()));

        Optional<UserDataContext> context = userDataContextRepository.findByUser_Id(userId);
        User u = userRepository.findById(userId).orElseThrow();
        String currentData = analyticsService.buildContextSummary(
                analyticsService.buildDashboard(currentPeriod, userId));
        String previousData = analyticsService.buildContextSummary(
                analyticsService.buildDashboard(previousPeriod, userId));

        if (context.isEmpty()) {
            UserDataContext newContext = new UserDataContext();
            newContext.setUser(u);
            newContext.setCurrentData(currentData);
            newContext.setPreviousData(previousData);
            return userDataContextRepository.save(newContext);
        } else {
            UserDataContext existing = context.get();
            existing.setCurrentData(currentData);
            existing.setPreviousData(previousData);
            return userDataContextRepository.save(existing);
        }
    }

    @Override
    public UserDataContext getUserDataContext(Long userId) {
        return userDataContextRepository.findByUser_Id(userId)
                .orElseGet(() -> updateUserDataContext(userId));
    }
}
