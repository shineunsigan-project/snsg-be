package com.shineunsigan.snsg.user.domain.repository;

import java.util.List;

import com.shineunsigan.snsg.user.domain.model.TermsAgreement;

public interface TermsAgreementRepository {

    List<TermsAgreement> saveAll(List<TermsAgreement> termsAgreements);
}
