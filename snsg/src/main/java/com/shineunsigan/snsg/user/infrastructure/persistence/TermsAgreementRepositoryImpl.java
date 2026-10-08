package com.shineunsigan.snsg.user.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.shineunsigan.snsg.user.domain.model.TermsAgreement;
import com.shineunsigan.snsg.user.domain.repository.TermsAgreementRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TermsAgreementRepositoryImpl implements TermsAgreementRepository {

    private final TermsAgreementJpaRepository termsAgreementJpaRepository;

    @Override
    public List<TermsAgreement> saveAll(List<TermsAgreement> termsAgreements) {
        return termsAgreementJpaRepository.saveAll(termsAgreements);
    }

    @Override
    public List<TermsAgreement> findByUserId(UUID userId) {
        return termsAgreementJpaRepository.findByUserIdAndIsDeletedFalse(userId);
    }
}
