package com.elias.mismonedas.identity.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.identity.dto.user.UserPreferencesRequest;
import com.elias.mismonedas.identity.dto.user.UserPreferencesResponse;
import com.elias.mismonedas.identity.mapper.UserPreferencesMapper;
import com.elias.mismonedas.identity.repository.UserPreferencesRepository;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import com.elias.mismonedas.identity.service.UserPreferencesService;
import com.elias.mismonedas.shared.exception.CurrencyChangeRateLimitException;
import com.elias.mismonedas.shared.exception.UserPreferencesNotFoundException;
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
    private final SecurityService securityService;

    @Override
    @Transactional(readOnly = true)
    public UserPreferencesResponse findByUserId() {
        Long userId = securityService.getCurrentUser().getId();
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
                    return createFor(userId, defaults);
                });
    }

    @Override
    @Transactional
    public UserPreferencesResponse create(UserPreferencesRequest request) {
        return createFor(securityService.getCurrentUser().getId(), request);
    }

    private UserPreferencesResponse createFor(Long userId, UserPreferencesRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        return repository.findByUser_Id(userId)
                .map(existing -> {
                    log.info("Preferencias ya existen para userId={}, devolviendo existentes", userId);
                    return mapper.toResponse(existing);
                })
                .orElseGet(() -> {
                    UserPreferences prefs = mapper.toEntity(request);
                    prefs.setUser(user);
                    repository.save(prefs);
                    log.info("Preferencias creadas para userId={}", userId);
                    return mapper.toResponse(prefs);
                });
    }

    @Override
    @Transactional
    public UserPreferencesResponse update(UserPreferencesRequest request) {
        Long userId = securityService.getCurrentUser().getId();
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
