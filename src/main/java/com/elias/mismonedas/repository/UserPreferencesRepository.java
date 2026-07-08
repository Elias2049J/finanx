package com.elias.mismonedas.repository;

import com.elias.mismonedas.entity.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {

    Optional<UserPreferences> findByUser_Id(Long userId);

    void deleteByUser_Id(Long userId);
}
