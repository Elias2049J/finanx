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

    Optional<FcmToken> findFirstByUser_IdAndToken(Long userId, String token);

    int deleteByUpdatedAtBefore(LocalDateTime cutoff);

    @Query("SELECT f FROM FcmToken f WHERE f.user.id = :userId AND f.token = :token ORDER BY f.id ASC")
    List<FcmToken> findAllByUser_IdAndToken(@Param("userId") Long userId, @Param("token") String token);






    @Modifying
    @Query("delete from FcmToken f where f.id = :id and f.user.id = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
