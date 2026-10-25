package com.elias.mismonedas.identity.repository;

import com.elias.mismonedas.identity.domain.entity.User;
import com.elias.mismonedas.identity.domain.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByAuthProviderAndProviderUserId(AuthProvider authProvider, String providerUserId);

    @Query("""
            select u from User u
            where u.id = :userId
              and exists (
                  select r.id from RefreshToken r
                  where r.user.id = u.id
                    and r.familyId = :sessionId
                    and r.revokedAt is null
                    and r.expiresAt > :now
              )
            """)
    Optional<User> findByIdAndActiveRefreshFamily(
            @Param("userId") Long userId,
            @Param("sessionId") String sessionId,
            @Param("now") OffsetDateTime now
    );
}
