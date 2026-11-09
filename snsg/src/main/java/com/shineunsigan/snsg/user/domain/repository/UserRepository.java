package com.shineunsigan.snsg.user.domain.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.shineunsigan.snsg.user.domain.model.User;

/**
 * 조회는 삭제되지 않은 행만 본다. 삭제된 행까지 봐야 하는 메서드는 이름에 드러낸다.
 */
public interface UserRepository {

    /**
     * 바로 DB에 반영한다. 닉네임 유니크 인덱스를 위반하면(동시 가입 경합)
     * {@code BusinessException(DUPLICATED_NICKNAME)}을 던진다.
     */
    User save(User user);

    Optional<User> findById(UUID id);

    /**
     * 닉네임 중복 판정(POL-U3)을 여기서만 한다. 대소문자를 무시하고 비교한다.
     * 탈퇴하지 않은 회원이 쓰고 있거나, 삭제되지 않은 닉네임 이력 중 보호 기간({@code reserved_until})이
     * {@code now}보다 뒤인 행이 있으면 참이다. 탈퇴한 회원의 현재 닉네임은 보지 않는다.
     */
    boolean isNicknameTaken(String nickname, LocalDateTime now);

    /**
     * 탈퇴하지 않은 다른 회원이 이 사진 경로를 쓰고 있는가. 우리 업로드 경로는 한 회원만 참조해야 한다.
     * 남의 경로를 자기 사진으로 넣으면, 사진을 바꾸거나 탈퇴할 때 남의 파일이 지워지기 때문이다.
     *
     * @param userId 본인. 본인이 쓰는 것은 세지 않는다. 가입처럼 본인이 아직 없으면 {@code null}
     */
    boolean isProfileImageUsedByOthers(String profileImageUrl, UUID userId);
}
