package com.shineunsigan.snsg.admin.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shineunsigan.snsg.admin.domain.model.Admin;

import jakarta.persistence.LockModeType;

interface AdminJpaRepository extends JpaRepository<Admin, UUID> {

    Optional<Admin> findByIdAndIsDeletedFalse(UUID id);

    /** 어드민 행 하나만 잠근다. 어드민에는 연관이 없다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Admin a where a.id = :id and a.isDeleted = false")
    Optional<Admin> findByIdForUpdate(@Param("id") UUID id);

    Optional<Admin> findByLoginIdAndIsDeletedFalse(String loginId);

    boolean existsByLoginIdAndIsDeletedFalse(String loginId);
}
