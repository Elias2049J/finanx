package com.elias.finanx.repository;

import com.elias.finanx.entity.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {

    Optional<UserPreferences> findByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM UserPreferences p WHERE p.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
