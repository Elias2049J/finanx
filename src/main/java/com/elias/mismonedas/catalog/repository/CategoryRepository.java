package com.elias.mismonedas.catalog.repository;

import com.elias.mismonedas.catalog.domain.entity.Category;
import com.elias.mismonedas.ledger.domain.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByUser_Id(Long userId);

    Long id(Long id);

    List<Category> findAllByUser_IdAndActive(Long userId, Boolean active);

    void deleteAllByUser_Id(Long userId);

    @Query("""
            SELECT c FROM Category c
            WHERE c.user.id = :userId
              AND c.active = true
              AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY c.name
            """)
    List<Category> findByUserIdAndKeyword(@Param("userId") Long userId, @Param("keyword") String keyword);

    @Query("""
            SELECT c FROM Category c
            WHERE c.user.id = :userId
              AND c.active = true
              AND c.type = :type
              AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY c.name
            """)
    List<Category> findByUserIdAndTypeAndKeyword(@Param("userId") Long userId,
                                                  @Param("type") TransactionType type,
                                                  @Param("keyword") String keyword);

    @Query("""
            SELECT c FROM Category c
            WHERE c.user.id = :userId
              AND c.active = true
              AND c.user IS NULL
              AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY c.name
            """)
    List<Category> findGlobalByKeyword(@Param("keyword") String keyword);
}

