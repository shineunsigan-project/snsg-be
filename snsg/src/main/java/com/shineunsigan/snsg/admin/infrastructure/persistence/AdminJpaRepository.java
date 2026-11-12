package com.shineunsigan.snsg.admin.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shineunsigan.snsg.admin.domain.model.Admin;

interface AdminJpaRepository extends JpaRepository<Admin, UUID> {

    Optional<Admin> findByIdAndIsDeletedFalse(UUID id);

    Optional<Admin> findByLoginIdAndIsDeletedFalse(String loginId);

    boolean existsByLoginIdAndIsDeletedFalse(String loginId);
}
