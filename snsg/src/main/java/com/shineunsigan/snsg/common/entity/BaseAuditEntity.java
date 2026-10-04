package com.shineunsigan.snsg.common.entity;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 누가 만들고 누가 마지막으로 바꿨는지가 필요한 엔티티가 상속한다.
 * 삭제도 수정이므로 {@code updatedBy}가 삭제한 주체를 가리킨다.
 * 값은 사용자 UUID 문자열이거나 {@link Auditor}의 시스템 주체다.
 */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseAuditEntity extends BaseEntity {

    @CreatedBy
    @Column(nullable = false, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(nullable = false)
    private String updatedBy;
}
