package com.elias.finanx.repository;

import com.elias.finanx.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    List<User> findAllByNameContainingIgnoreCase(String name);
}
