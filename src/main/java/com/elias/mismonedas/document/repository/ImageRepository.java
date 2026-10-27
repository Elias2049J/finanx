package com.elias.mismonedas.document.repository;

import com.elias.mismonedas.document.domain.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {

    Optional<Image> findByIdAndUser_Id(Long imageId, Long userId);

    List<Image> findAllByUser_Id(Long userId);
}
