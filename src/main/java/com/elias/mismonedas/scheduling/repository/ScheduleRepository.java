package com.elias.mismonedas.scheduling.repository;

import com.elias.mismonedas.scheduling.domain.entity.Schedule;
import com.elias.mismonedas.scheduling.domain.enums.ScheduleState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    Optional<Schedule> findByIdAndUser_Id(Long id, Long userId);

    List<Schedule> findAllByUser_IdAndActiveAndState(Long userId, Boolean active, ScheduleState state);

    List<Schedule> findAllByUser_Id(Long userId);

    List<Schedule> findAllByUser_IdAndActiveTrue(Long userId);

    Page<Schedule> findAllByUser_IdAndActiveTrue(Long userId, Pageable pageable);

    Page<Schedule> findAllByUser_IdAndActiveAndState(Long userId, Boolean active, ScheduleState state, Pageable pageable);

    void deleteAllByUser_Id(Long userId);

    /**
     * Toma la ocurrencia solo si sigue siendo la esperada.
     *
     * <p>Debe ser SQL nativo: un UPDATE JPQL sobre {@link Schedule} (herencia
     * JOINED) se traduce a un UPDATE con CTE. El SELECT del CTE se evalua antes
     * de esperar el lock de fila, por lo que un segundo nodo ve la fila vieja y
     * actualiza por id sin revalidar {@code proxima_ejecucion}. El UPDATE nativo
     * revalida el WHERE despues del lock y garantiza que solo un nodo gane.</p>
     */
    @Modifying
    @Query(value = """
            UPDATE programacion
               SET ultima_ejecucion = :expected
             WHERE id = :id
               AND proxima_ejecucion = :expected
               AND estado = :state
               AND activo = true
            """, nativeQuery = true)
    int claimOccurrence(@Param("id") Long id,
                        @Param("expected") OffsetDateTime expected,
                        @Param("state") String state);

    /**
     * Marca la ocurrencia vencida como pendiente de confirmacion sin materializarla.
     *
     * <p>Igual que {@link #claimOccurrence}, usa SQL nativo para revalidar estado y
     * fecha despues del lock de fila; un UPDATE JPQL sobre la jerarquia JOINED se
     * traduce a un UPDATE con CTE que no revalida correctamente en concurrencia.</p>
     */
    @Modifying
    @Query(value = """
            UPDATE programacion
               SET estado = 'PENDING_CONFIRMATION'
             WHERE id = :id
               AND proxima_ejecucion = :expected
               AND estado = 'RUNNING'
               AND activo = true
            """, nativeQuery = true)
    int markPendingConfirmation(@Param("id") Long id,
                                @Param("expected") OffsetDateTime expected);

    /**
     * Toma la ocurrencia pendiente solo si sigue siendo la esperada. Solo un
     * confirmador u omisor puede materializarla; el resto recibe {@code 0}.
     *
     * <p>Mismo motivo que {@link #claimOccurrence} para usar SQL nativo.</p>
     */
    @Modifying
    @Query(value = """
            UPDATE programacion
               SET estado = 'RUNNING'
             WHERE id = :id
               AND proxima_ejecucion = :expected
               AND estado = 'PENDING_CONFIRMATION'
               AND activo = true
            """, nativeQuery = true)
    int claimPendingConfirmation(@Param("id") Long id,
                                 @Param("expected") OffsetDateTime expected);
}
