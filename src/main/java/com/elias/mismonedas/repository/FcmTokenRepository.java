package com.elias.mismonedas.repository;

import com.elias.mismonedas.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    List<FcmToken> findAllByUser_Id(Long userId);

    Optional<FcmToken> findByToken(String token);

    Optional<FcmToken> findByUser_IdAndToken(Long userId, String token);

    int deleteByUpdatedAtBefore(LocalDateTime cutoff);
}
