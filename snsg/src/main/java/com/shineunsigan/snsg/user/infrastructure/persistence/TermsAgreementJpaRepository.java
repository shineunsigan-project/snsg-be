package com.shineunsigan.snsg.user.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shineunsigan.snsg.user.domain.model.TermsAgreement;

interface TermsAgreementJpaRepository extends JpaRepository<TermsAgreement, UUID> {

    List<TermsAgreement> findByUserIdAndIsDeletedFalse(UUID userId);
}
