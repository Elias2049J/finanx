package com.elias.mismonedas.repository;

import com.elias.mismonedas.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByUser_Id(Long userId);

    Long id(Long id);

    List<Category> findAllByUser_IdAndActive(Long userId, Boolean active);

    void deleteAllByUser_Id(Long userId);
}

