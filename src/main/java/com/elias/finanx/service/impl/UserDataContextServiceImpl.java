package com.elias.finanx.service.impl;

import com.elias.finanx.dto.date.PeriodRequest;
import com.elias.finanx.entity.User;
import com.elias.finanx.entity.UserDataContext;
import com.elias.finanx.repository.UserDataContextRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.UserDataContextService;
import com.elias.finanx.service.analytics.AnalyticsService;
import org.apache.http.HttpException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
    public UserDataContext updateUserDataContext(Long userId) throws HttpException, IOException {
        LocalDate currentDate = LocalDate.now();
        LocalDate previousMonth = currentDate.minusMonths(1);

        PeriodRequest currentPeriod = new PeriodRequest();
        currentPeriod.setUserId(userId);
        currentPeriod.setStart(currentDate.withDayOfMonth(1));
        currentPeriod.setEnd(currentDate);

        PeriodRequest previousPeriod = new PeriodRequest();
        previousPeriod.setUserId(userId);
        previousPeriod.setStart(previousMonth.withDayOfMonth(1));
        previousPeriod.setEnd(previousMonth.withDayOfMonth(previousMonth.lengthOfMonth()));

        Optional<UserDataContext> context = userDataContextRepository.findByUser_Id(userId);
        User u = userRepository.findById(userId).orElseThrow();
        String currentData = analyticsService.buildContextSummary(
                analyticsService.buildDashboard(currentPeriod));
        String previousData = analyticsService.buildContextSummary(
                analyticsService.buildDashboard(previousPeriod));

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
                .orElseGet(() -> {
                    try {
                        return updateUserDataContext(userId);
                    } catch (HttpException | IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
