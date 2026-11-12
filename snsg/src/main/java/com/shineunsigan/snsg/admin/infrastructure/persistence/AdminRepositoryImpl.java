package com.shineunsigan.snsg.admin.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.admin.domain.model.Admin;
import com.shineunsigan.snsg.admin.domain.repository.AdminRepository;
import com.shineunsigan.snsg.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AdminRepositoryImpl implements AdminRepository {

    private static final String LOGIN_ID_INDEX = "uix_admins_login_id";

    private final AdminJpaRepository adminJpaRepository;

    /**
     * 인덱스 위반이 이 호출에서 드러나도록 바로 flush한다. 커밋 때 드러나면 500이 된다.
     * 가입은 잠금 없이 경쟁하므로 아이디 인덱스 위반을 409로 바꾼다. 다른 제약 위반은 그대로 던진다.
     */
    @Override
    public Admin save(Admin admin) {
        try {
            return adminJpaRepository.saveAndFlush(admin);
        } catch (DataIntegrityViolationException exception) {
            if (violatesLoginIdIndex(exception)) {
                throw new BusinessException(AdminErrorCode.DUPLICATED_LOGIN_ID);
            }
            throw exception;
        }
    }

    @Override
    public Optional<Admin> findById(UUID id) {
        return adminJpaRepository.findByIdAndIsDeletedFalse(id);
    }

    @Override
    public Optional<Admin> findByLoginId(String loginId) {
        return adminJpaRepository.findByLoginIdAndIsDeletedFalse(loginId);
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return adminJpaRepository.existsByLoginIdAndIsDeletedFalse(loginId);
    }

    private boolean violatesLoginIdIndex(DataIntegrityViolationException exception) {
        return exception.getCause() instanceof ConstraintViolationException violation
                && LOGIN_ID_INDEX.equals(violation.getConstraintName());
    }
}
