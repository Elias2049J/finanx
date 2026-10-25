package com.elias.mismonedas.notifications.repository;

import com.elias.mismonedas.notifications.domain.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    List<FcmToken> findAllByUser_Id(Long userId);

    void deleteAllByUser_Id(Long userId);

    Page<FcmToken> findAllByUser_Id(Long userId, Pageable pageable);

    Optional<FcmToken> findByToken(String token);

    int deleteByUpdatedAtBefore(LocalDateTime cutoff);







    @Modifying
    @Query("delete from FcmToken f where f.id = :id and f.user.id = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into public.fcm_tokens
                (user_id, token, nombre_dispositivo, fecha_creacion, fecha_actualizacion)
            values (:userId, :token, :deviceName, :now, :now)
            on conflict on constraint uq_fcm_tokens_token
            do update set user_id = excluded.user_id,
                          nombre_dispositivo = coalesce(excluded.nombre_dispositivo, public.fcm_tokens.nombre_dispositivo),
                          fecha_actualizacion = excluded.fecha_actualizacion
            """, nativeQuery = true)
    void upsertByToken(
            @Param("userId") Long userId,
            @Param("token") String token,
            @Param("deviceName") String deviceName,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("delete from FcmToken f where f.token = :token and f.user.id = :userId")
    int deleteByTokenAndUserId(@Param("token") String token, @Param("userId") Long userId);
}
