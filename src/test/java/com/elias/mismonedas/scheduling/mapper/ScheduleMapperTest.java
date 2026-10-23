package com.elias.mismonedas.scheduling.mapper;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.entity.UserPreferences;
import com.elias.mismonedas.identity.domain.enums.TimeZone;
import com.elias.mismonedas.scheduling.domain.entity.BudgetSchedule;
import com.elias.mismonedas.scheduling.domain.entity.RecurrenceRule;
import com.elias.mismonedas.scheduling.domain.enums.RecurrenceType;
import com.elias.mismonedas.scheduling.dto.schedule.BudgetScheduleResponse;
import com.elias.mismonedas.shared.mapper.DateMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el mapeo real de {@link ScheduleMapper} (implementacion generada por
 * MapStruct), no un mock.
 *
 * La moneda del presupuesto programado se resuelve ahora en la capa de servicio
 * (ScheduleServiceImpl) a partir de la billetera principal, no aqui: el mapper
 * deja {@code currencyCode} en null y el servicio lo completa.
 */
class ScheduleMapperTest {

    private final ScheduleMapper mapper = mapperWithDependencies();

    /**
     * Los mappers generados usan inyeccion de Spring (@Autowired), que
     * Mappers.getMapper() no resuelve. Se cablean a mano para poder ejercitar
     * la implementacion real sin levantar el contexto.
     */
    private static ScheduleMapper mapperWithDependencies() {
        DateMapper dateMapper = Mappers.getMapper(DateMapper.class);
        RecurrenceRuleMapper recurrenceRuleMapper = Mappers.getMapper(RecurrenceRuleMapper.class);
        ReflectionTestUtils.setField(recurrenceRuleMapper, "dateMapper", dateMapper);

        ScheduleMapper scheduleMapper = Mappers.getMapper(ScheduleMapper.class);
        ReflectionTestUtils.setField(scheduleMapper, "dateMapper", dateMapper);
        ReflectionTestUtils.setField(scheduleMapper, "recurrenceRuleMapper", recurrenceRuleMapper);
        return scheduleMapper;
    }

    @Test
    void shouldLeaveCurrencyCodeNullForServiceResolution() {
        User user = userWithCurrency("USD");
        BudgetSchedule schedule = new BudgetSchedule();
        schedule.setId(1L);
        schedule.setUser(user);
        schedule.setLimitAmount(new BigDecimal("1500.00"));
        schedule.setPercentAlert(80);
        schedule.setAlertable(true);
        schedule.setDescription("Comida");
        schedule.setRecurrenceRule(recurrenceRuleOf(user));

        BudgetScheduleResponse response = mapper.toResponse(schedule);

        assertThat(response.getCurrencyCode()).isNull();
        assertThat(response.getLimitAmount()).isEqualByComparingTo("1500.00");
        assertThat(response.getType()).isEqualTo("BUDGET");
        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    void shouldReturnNullCurrencyWhenUserHasNoPreferences() {
        User user = new User();
        BudgetSchedule schedule = new BudgetSchedule();
        schedule.setId(2L);
        schedule.setUser(user);
        schedule.setLimitAmount(BigDecimal.TEN);
        schedule.setRecurrenceRule(recurrenceRuleOf(user));

        BudgetScheduleResponse response = mapper.toResponse(schedule);

        assertThat(response.getCurrencyCode()).isNull();
        assertThat(response.getLimitAmount()).isEqualByComparingTo("10");
    }

    private User userWithCurrency(String currencyCode) {
        User user = new User();
        user.setPreferences(UserPreferences.builder()
                .currencyCode(currencyCode)
                .timeZone(TimeZone.AMERICA_LIMA)
                .build());
        return user;
    }

    private RecurrenceRule recurrenceRuleOf(User user) {
        RecurrenceRule rule = new RecurrenceRule();
        rule.setUser(user);
        rule.setRecurrenceType(RecurrenceType.MONTHLY);
        rule.setInterval(1);
        rule.setStart(OffsetDateTime.now());
        return rule;
    }
}
