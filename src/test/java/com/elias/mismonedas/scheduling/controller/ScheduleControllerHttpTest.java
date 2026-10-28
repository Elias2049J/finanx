package com.elias.mismonedas.scheduling.controller;

import com.elias.mismonedas.scheduling.dto.schedule.OccurrenceConfirmationResponse;
import com.elias.mismonedas.scheduling.dto.schedule.OccurrenceKeyRequest;
import com.elias.mismonedas.scheduling.service.ScheduleService;
import com.elias.mismonedas.shared.config.GlobalExceptionHandler;
import com.elias.mismonedas.shared.exception.ConflictException;
import com.elias.mismonedas.shared.i18n.CurrentLocaleProvider;
import com.elias.mismonedas.shared.i18n.JsonMessageCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScheduleControllerHttpTest {

    private static final String CONFIRM_URL = "/api/v1/schedule/12/confirm";
    private static final String IGNORE_URL = "/api/v1/schedule/12/ignore";
    private static final String KEY = "schedule:12:1761700000000";

    private final ScheduleService scheduleService = mock(ScheduleService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ScheduleController(scheduleService))
            .setControllerAdvice(new GlobalExceptionHandler(createMessageCatalog(), new CurrentLocaleProvider()))
            .build();

    private static JsonMessageCatalog createMessageCatalog() {
        JsonMessageCatalog catalog = new JsonMessageCatalog(new ObjectMapper());
        catalog.initialize();
        return catalog;
    }

    @Test
    void confirmReturnsMaterializedTransactionId() throws Exception {
        when(scheduleService.confirm(eq(12L), any(OccurrenceKeyRequest.class)))
                .thenReturn(new OccurrenceConfirmationResponse(KEY, 42L, null));

        mockMvc.perform(post(CONFIRM_URL)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OccurrenceKeyRequest(KEY))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occurrenceKey").value(KEY))
                .andExpect(jsonPath("$.transactionId").value(42));
    }

    @Test
    void confirmRequiresOccurrenceKey() throws Exception {
        mockMvc.perform(post(CONFIRM_URL)
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(scheduleService, never()).confirm(any(), any());
    }

    @Test
    void confirmReturnsConflictForStaleOccurrence() throws Exception {
        when(scheduleService.confirm(eq(12L), any(OccurrenceKeyRequest.class)))
                .thenThrow(new ConflictException("Occurrence is no longer pending"));

        mockMvc.perform(post(CONFIRM_URL)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OccurrenceKeyRequest(KEY))))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmReturnsForbiddenForForeignSchedule() throws Exception {
        when(scheduleService.confirm(eq(12L), any(OccurrenceKeyRequest.class)))
                .thenThrow(new AccessDeniedException("not yours"));

        mockMvc.perform(post(CONFIRM_URL)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OccurrenceKeyRequest(KEY))))
                .andExpect(status().isForbidden());
    }

    @Test
    void ignoreReturnsNoContent() throws Exception {
        mockMvc.perform(post(IGNORE_URL)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OccurrenceKeyRequest(KEY))))
                .andExpect(status().isNoContent());

        verify(scheduleService).ignore(eq(12L), any(OccurrenceKeyRequest.class));
    }
}
