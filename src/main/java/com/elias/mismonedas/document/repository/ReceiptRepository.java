package com.elias.mismonedas.document.repository;

import com.elias.mismonedas.document.domain.entity.Receipt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    List<Receipt> findAllByUser_Id(Long userId);

    @Query("select r.id from Receipt r where r.user.id = :userId")
    List<Long> findIdsByUserId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("delete from Receipt r where r.user.id = :userId")
    int deleteAllRowsByUserId(@Param("userId") Long userId);

    Optional<Receipt> findByIdAndUser_Id(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Receipt r where r.id = :id")
    Optional<Receipt> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Receipt r where r.id = :id and r.user.id = :userId")
    Optional<Receipt> findByIdAndUser_IdForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
