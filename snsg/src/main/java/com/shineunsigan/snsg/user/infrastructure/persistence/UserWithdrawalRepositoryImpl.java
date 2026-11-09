package com.shineunsigan.snsg.user.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.shineunsigan.snsg.user.domain.model.AnonymizeCursor;
import com.shineunsigan.snsg.user.domain.model.UserWithdrawal;
import com.shineunsigan.snsg.user.domain.repository.UserWithdrawalRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UserWithdrawalRepositoryImpl implements UserWithdrawalRepository {

    private final UserWithdrawalJpaRepository userWithdrawalJpaRepository;
    private final EntityManager entityManager;

    @Override
    public UserWithdrawal save(UserWithdrawal withdrawal) {
        return userWithdrawalJpaRepository.save(withdrawal);
    }

    @Override
    public Optional<UserWithdrawal> findByUserId(UUID userId) {
        return userWithdrawalJpaRepository.findByUserIdAndIsDeletedFalse(userId);
    }

    /**
     * 정렬과 커서 비교는 DB에서 한다. Java의 UUID 비교는 부호 있는 비교라 PostgreSQL uuid 순서와 다르다.
     */
    @Override
    public List<UserWithdrawal> findAnonymizeTargets(LocalDateTime now, AnonymizeCursor after, int size) {
        StringBuilder jpql = new StringBuilder("""
                select w from UserWithdrawal w
                where w.anonymized = false and w.isDeleted = false and w.anonymizeDueAt <= :now""");
        if (after != null) {
            jpql.append(" and (w.anonymizeDueAt > :cursorDueAt"
                    + " or (w.anonymizeDueAt = :cursorDueAt and w.user.id > :cursorUserId))");
        }
        jpql.append(" order by w.anonymizeDueAt, w.user.id");
        TypedQuery<UserWithdrawal> query = entityManager.createQuery(jpql.toString(), UserWithdrawal.class)
                .setParameter("now", now);
        if (after != null) {
            query.setParameter("cursorDueAt", after.anonymizeDueAt())
                    .setParameter("cursorUserId", after.userId());
        }
        return query.setMaxResults(size).getResultList();
    }
}
