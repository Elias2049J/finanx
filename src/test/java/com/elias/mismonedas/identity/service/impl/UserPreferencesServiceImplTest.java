package com.elias.mismonedas.identity.service.impl;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.dto.user.UserPreferencesRequest;
import com.elias.mismonedas.identity.mapper.UserPreferencesMapper;
import com.elias.mismonedas.identity.repository.UserPreferencesRepository;
import com.elias.mismonedas.identity.repository.UserRepository;
import com.elias.mismonedas.identity.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPreferencesServiceImplTest {

    @Mock private UserPreferencesRepository repository;
    @Mock private UserPreferencesMapper mapper;
    @Mock private UserRepository userRepository;
    @Mock private SecurityService securityService;

    private UserPreferencesServiceImpl service;
    private User currentUser;

    @BeforeEach
    void setUp() {
        service = new UserPreferencesServiceImpl(repository, mapper, userRepository, securityService);
        currentUser = new User();
        currentUser.setId(7L);
        when(securityService.getCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void shouldDefaultLocaleWhenCreatingPreferencesWithoutLocale() {
        UserPreferencesRequest request = new UserPreferencesRequest();
        UserPreferences preferences = new UserPreferences();
        when(userRepository.findById(7L)).thenReturn(Optional.of(currentUser));
        when(repository.findByUser_Id(7L)).thenReturn(Optional.empty());
        when(mapper.toEntity(request)).thenReturn(preferences);

        service.create(request);

        ArgumentCaptor<UserPreferences> saved = ArgumentCaptor.forClass(UserPreferences.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getLocale()).isEqualTo("es-PE");
    }

    @Test
    void shouldCanonicalizeLocaleWhenUpdatingPreferences() {
        UserPreferences preferences = UserPreferences.builder().locale("es-PE").build();
        UserPreferencesRequest request = new UserPreferencesRequest();
        request.setLocale("EN-us");
        when(repository.findByUser_Id(7L)).thenReturn(Optional.of(preferences));
        when(repository.save(any(UserPreferences.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(request);

        assertThat(preferences.getLocale()).isEqualTo("en-US");
    }

    @Test
    void shouldPreserveLocaleWhenUpdatingWithLegacyRequestWithoutLocale() {
        UserPreferences preferences = UserPreferences.builder().locale("en-US").build();
        UserPreferencesRequest request = new UserPreferencesRequest();
        when(repository.findByUser_Id(7L)).thenReturn(Optional.of(preferences));
        when(repository.save(any(UserPreferences.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(request);

        assertThat(preferences.getLocale()).isEqualTo("en-US");
    }
}
