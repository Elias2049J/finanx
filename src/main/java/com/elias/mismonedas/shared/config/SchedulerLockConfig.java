package com.elias.mismonedas.shared.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Lock distribuido (ShedLock) para todos los {@code @Scheduled} del proyecto.
 *
 * <p>Sin esto, cada instancia de la aplicación ejecutaría cada job por su cuenta:
 * en cuanto se escala a N réplicas detrás del balanceador, el trabajo se
 * multiplica por N. Con {@code @SchedulerLock}, cada método anotado adquiere una
 * fila en la tabla {@code shedlock} y solo la instancia que gana la carrera lo
 * ejecuta.</p>
 *
 * <p>Se usa {@code usingDbTime()} para tomar el reloj de la base de datos, de
 * modo que no haya desajustes de hora entre instancias.</p>
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }
}
