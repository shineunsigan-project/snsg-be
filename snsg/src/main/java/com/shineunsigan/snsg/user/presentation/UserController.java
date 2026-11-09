package com.shineunsigan.snsg.user.presentation;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shineunsigan.snsg.common.web.ApiResponse;
import com.shineunsigan.snsg.user.application.UserCommandService;
import com.shineunsigan.snsg.user.application.UserQueryService;
import com.shineunsigan.snsg.user.domain.constants.WithdrawReason;
import com.shineunsigan.snsg.user.presentation.dto.response.NicknameAvailabilityResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

@Tag(name = "사용자")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserCommandService userCommandService;
    private final UserQueryService userQueryService;

    @DeleteMapping("/me")
    @Operation(summary = "탈퇴",
            description = """
                    사용자를 탈퇴시킨다. 이름·이메일·닉네임·생년월일·프로필 사진은 비식별 값으로 바뀌고 올렸던 사진 파일은 지워진다.
                    카카오 연결도 끊어지므로 다시 로그인하면 동의 화면부터 시작한다.
                    되돌릴 수 없고, 다시 가입하면 이전 활동과 이어지지 않는 새 계정이 된다.
                    refresh token은 즉시 무효가 되지만 쿠키는 남는다. 탈퇴 직후 로그아웃 API를 한 번 호출해 쿠키를 비운다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "토큰이 없거나 유효하지 않다")})
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal UUID userId) {
        userCommandService.withdraw(userId, WithdrawReason.USER_REQUESTED);

        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 토큰이 선택인 공개 경로다. 유효한 토큰이면 userId가, 없거나 틀리면 null이 들어온다(익명).
     * swagger-ui에서 토큰을 넣어 본인 예외를 시험할 수 있도록 전역 bearer 요구를 지우지 않는다.
     */
    @GetMapping("/nickname-availability")
    @Operation(summary = "닉네임 사용 가능 여부",
            description = """
                    입력한 닉네임을 쓸 수 있는지 알려준다. 가입 닉네임 입력 화면과 프로필 수정 화면에서 닉네임 칸을 벗어날 때 부른다.
                    결과는 안내용이다. 가입·프로필 수정 API가 저장할 때 같은 검사를 다시 하므로, 여기서 true였어도 그 사이 다른 사람이 쓰면 가입·수정이 DUPLICATED_NICKNAME으로 실패할 수 있다.
                    토큰은 선택이다. 가입 중에는 토큰 없이 부른다. 토큰이 있으면 본인의 현재 닉네임은 사용 가능(true)으로 본다(대소문자 무시).
                    토큰이 틀리거나 만료돼도 401이 아니라 익명으로 처리한다. 이때는 본인 닉네임도 사용 중으로 나온다.
                    판정 순서: 형식(2~10자의 한글·영문·숫자·.·_)이 틀리면 INVALID_FORMAT, 본인의 현재 닉네임이면 true,
                    다른 회원이 쓰고 있거나 보호 기간(변경 전 14일, 탈퇴 90일)이 남았으면 DUPLICATED. 대소문자는 무시한다.
                    DUPLICATED는 사용 중과 보호 중을 구분하지 않는다. 보호 중임을 알리면 다른 회원의 탈퇴·변경 여부가 드러나기 때문이다.
                    조회만 하므로 같은 요청을 몇 번 다시 보내도 된다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "판정 결과. 형식 오류·중복도 200이고 data.available과 data.reason으로 알려준다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "INVALID_REQUEST: nickname 파라미터가 없다. 빈 값(?nickname=)은 400이 아니라 200 INVALID_FORMAT이다")})
    public ResponseEntity<ApiResponse<NicknameAvailabilityResponse>> checkNicknameAvailability(
            @Parameter(description = "확인할 닉네임. 2~10자의 한글·영문·숫자·.·_", example = "민트초코")
            @RequestParam String nickname,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(NicknameAvailabilityResponse.from(
                userQueryService.checkNicknameAvailability(nickname, userId))));
    }
}
