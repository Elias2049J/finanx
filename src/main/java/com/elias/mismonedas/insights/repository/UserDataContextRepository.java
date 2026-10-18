package com.elias.mismonedas.insights.repository;

import com.elias.mismonedas.insights.domain.entity.UserDataContext;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserDataContextRepository extends JpaRepository<UserDataContext, Long> {
    Optional<UserDataContext> findByUser_Id(Long userId);
}
