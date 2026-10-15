package com.elias.mismonedas.document.repository;

import com.elias.mismonedas.document.domain.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {

    Optional<Image> findByIdAndUser_Id(Long imageId, Long userId);
}
