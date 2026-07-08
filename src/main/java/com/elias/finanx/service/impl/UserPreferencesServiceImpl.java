package com.elias.finanx.service.impl;

import com.elias.finanx.dto.user.UserPreferencesRequest;
import com.elias.finanx.dto.user.UserPreferencesResponse;
import com.elias.finanx.entity.User;
import com.elias.finanx.entity.UserPreferences;
import com.elias.finanx.entity.enums.TimeZone;
import com.elias.finanx.exception.CurrencyChangeRateLimitException;
import com.elias.finanx.exception.UserPreferencesNotFoundException;
import com.elias.finanx.mapper.UserPreferencesMapper;
import com.elias.finanx.repository.UserPreferencesRepository;
import com.elias.finanx.repository.UserRepository;
import com.elias.finanx.service.UserPreferencesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPreferencesServiceImpl implements UserPreferencesService {

    private final UserPreferencesRepository repository;
    private final UserPreferencesMapper mapper;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserPreferencesResponse findByUserId(Long userId) {
        UserPreferences prefs = repository.findByUser_Id(userId)
                .orElseThrow(() -> new UserPreferencesNotFoundException(userId));
        return mapper.toResponse(prefs);
    }

    @Override
    @Transactional
    public UserPreferencesResponse getOrCreateDefaults(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        return repository.findByUser_Id(userId)
                .map(mapper::toResponse)
                .orElseGet(() -> {
                    UserPreferencesRequest defaults = new UserPreferencesRequest();
                    defaults.setTimeZone(TimeZone.AMERICA_LIMA);
                    defaults.setCurrencyCode("PEN");
                    defaults.setEmailNotifications(true);
                    return create(userId, defaults);
                });
    }

    @Override
    @Transactional
    public UserPreferencesResponse create(Long userId, UserPreferencesRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (repository.findByUser_Id(userId).isPresent()) {
            throw new RuntimeException("Preferences already exist for user " + userId);
        }

        UserPreferences prefs = mapper.toEntity(request);
        prefs.setUser(user);
        repository.save(prefs);
        log.info("Preferencias creadas para userId={}", userId);
        return mapper.toResponse(prefs);
    }

    @Override
    @Transactional
    public UserPreferencesResponse update(Long userId, UserPreferencesRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        UserPreferences prefs = repository.findByUser_Id(userId)
                .orElseThrow(() -> new UserPreferencesNotFoundException(userId));

        String newCurrency = request.getCurrencyCode();
        String currentCurrency = prefs.getCurrencyCode();
        if (!newCurrency.equals(currentCurrency)) {
            int currentMonth = YearMonth.now().getYear() * 100 + YearMonth.now().getMonthValue();
            Integer lastMonth = prefs.getCurrencyChangeMonth();
            Integer count = prefs.getCurrencyChangeCount();
            if (count != null && lastMonth != null && lastMonth == currentMonth && count >= 2) {
                throw new CurrencyChangeRateLimitException(userId);
            }
            if (lastMonth != null && lastMonth == currentMonth) {
                prefs.setCurrencyChangeCount(count != null ? count + 1 : 1);
            } else {
                prefs.setCurrencyChangeCount(1);
            }
            prefs.setCurrencyChangeMonth(currentMonth);
            log.info("Currency change #{} for userId={} in month={}: {} -> {}",
                    prefs.getCurrencyChangeCount(), userId, currentMonth, currentCurrency, newCurrency);
        }

        mapper.updateFromDto(request, prefs);
        repository.save(prefs);
        log.info("Preferencias actualizadas para userId={}", userId);
        return mapper.toResponse(prefs);
    }
}
