package com.shineunsigan.snsg.place.domain.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서비스 지역(시군구). 행정구역 코드가 키라서 {@code BaseEntity}를 상속하지 않는다.
 * 마이그레이션으로만 관리하므로 쓰기 경로가 없다. 지역을 빼려면 삭제하지 않고 {@code isActive}를 끈다.
 */
@Getter
@Entity
@Table(name = "regions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 5, nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String sidoName;

    @Column(nullable = false)
    private String sigunguName;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean isActive;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
