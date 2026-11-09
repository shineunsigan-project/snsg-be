package com.shineunsigan.snsg.user.presentation;

import java.io.IOException;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.shineunsigan.snsg.common.web.ApiResponse;
import com.shineunsigan.snsg.user.application.UploadProfileImageFacade;
import com.shineunsigan.snsg.user.application.UserProfileCommandService;
import com.shineunsigan.snsg.user.application.UserQueryService;
import com.shineunsigan.snsg.user.application.UserWithdrawalCommandService;
import com.shineunsigan.snsg.user.domain.constants.WithdrawReason;
import com.shineunsigan.snsg.user.presentation.dto.request.UpdateMyProfileRequest;
import com.shineunsigan.snsg.user.presentation.dto.response.MyProfileResponse;
import com.shineunsigan.snsg.user.presentation.dto.response.NicknameAvailabilityResponse;
import com.shineunsigan.snsg.user.presentation.dto.response.ProfileImageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@Tag(name = "사용자")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserWithdrawalCommandService userWithdrawalCommandService;
    private final UserQueryService userQueryService;
    private final UserProfileCommandService userProfileCommandService;
    private final UploadProfileImageFacade uploadProfileImageFacade;

    @GetMapping("/me")
    @Operation(summary = "내 프로필 조회",
            description = """
                    로그인한 사용자의 프로필을 돌려준다. 내 정보 화면(MY-01)에 들어갈 때와 프로필 수정 화면(MY-02)을 열 때 부른다.
                    email은 등록하지 않았으면 빈 문자열이다(null이 아니다).
                    ageGroup은 오늘(한국 시간) 기준 만 나이로 계산한다. decade는 십의 자리 × 10이고,
                    phase는 EARLY 초반(일의 자리 0~3), MID 중반(4~6), LATE 후반(7~9)이다. 만 18·19세는 10대 후반(10, LATE)이다.
                    nicknameChangeableAt은 닉네임을 다시 바꿀 수 있는 시각이다. null이면 지금 바꿀 수 있다(바꾼 적이 없을 때 포함).
                    값이 있으면 프로필 수정 화면에서 닉네임 칸을 막고 그 날짜를 안내한다.
                    조회만 하므로 같은 요청을 몇 번 다시 보내도 된다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 프로필"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHENTICATED: 토큰이 없거나 만료·무효. 재발급 후 다시 부르거나 로그인 화면으로 간다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "USER_NOT_FOUND: 토큰의 사용자가 없거나 탈퇴했다. 로그인 화면으로 간다")})
    public ResponseEntity<ApiResponse<MyProfileResponse>> getMyProfile(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(MyProfileResponse.from(userQueryService.getMyProfile(userId))));
    }

    @PatchMapping("/me")
    @Operation(summary = "내 프로필 수정",
            description = """
                    닉네임·이메일·프로필 사진을 바꾼다. 프로필 수정 화면(MY-02)의 [저장]에서 부른다. 응답은 내 프로필 조회와 같은 형태다.
                    바꿀 필드만 보낸다. 생략한 필드는 그대로 둔다. 현재 값과 정확히 같은 값은 바꾸지 않은 것으로 본다(닉네임 변경 제한에도 세지 않는다).
                    이메일을 빈 문자열("")로 보내면 이메일을 지운다.
                    프로필 사진은 프로필 사진 업로드 API(POST /api/v1/users/me/profile-image)가 돌려준 경로만 받는다. 사진을 바꾸면 이전에 올렸던 사진 파일은 지워진다.
                    이름·성별·생년월일은 바꿀 수 없다. 요청에 넣어도 무시한다. 화면에서는 읽기 전용으로 보여준다.
                    닉네임은 14일에 한 번만 바꿀 수 있다. 마지막 변경 후 14일 안에 다른 닉네임을 보내면 409 NICKNAME_CHANGE_RESTRICTED다.
                    가입 후 첫 변경은 제한이 없다. 다시 바꿀 수 있는 시각은 응답과 내 프로필 조회의 nicknameChangeableAt으로 알 수 있다.
                    바꾸기 전 닉네임은 14일 동안 다른 사람이 쓰지 못한다.
                    검사 순서는 형식 → 닉네임 변경 제한 → 닉네임 중복이다. 하나라도 실패하면 아무것도 저장하지 않는다.
                    같은 요청을 다시 보내도 된다. 두 번째 요청은 바뀐 것이 없어 그대로 성공한다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "수정 완료. data는 바뀐 뒤의 내 프로필이다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = """
                            INVALID_REQUEST: 닉네임·이메일 형식 위반. errors에 필드(nickname, email)별 문구가 담긴다. 해당 칸 아래에 문구를 보여준다.
                            INVALID_PROFILE_IMAGE_URL: 업로드 API가 준 경로가 아니거나(빈 문자열·형식이 바뀐 경로 포함) 다른 회원이 쓰고 있는 경로다. 사진을 다시 올리게 한다.
                            INVALID_NICKNAME·INVALID_EMAIL: 서버의 안전망. 요청 검증을 통과한 값이면 나지 않는다"""),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHENTICATED: 토큰이 없거나 만료·무효. 재발급 후 다시 부르거나 로그인 화면으로 간다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "USER_NOT_FOUND: 토큰의 사용자가 없거나 탈퇴했다. 로그인 화면으로 간다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = """
                            DUPLICATED_NICKNAME: 다른 회원이 쓰고 있거나 보호 기간이 남은 닉네임이다(대소문자 무시). 다른 닉네임을 입력하게 한다.
                            NICKNAME_CHANGE_RESTRICTED: 마지막 닉네임 변경 후 14일이 지나지 않았다. 내 프로필 조회의 nicknameChangeableAt으로 바꿀 수 있는 날짜를 안내한다""")})
    public ResponseEntity<ApiResponse<MyProfileResponse>> updateMyProfile(@AuthenticationPrincipal UUID userId,
            @Valid @RequestBody UpdateMyProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(MyProfileResponse.from(
                userProfileCommandService.updateMyProfile(request.toCommand(userId)))));
    }

    @PostMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "프로필 사진 업로드",
            description = """
                    프로필 수정 화면(MY-02)에서 사진을 고르면 부른다. multipart 파트 이름은 image다.
                    저장만 하고 프로필에는 반영하지 않는다. 돌려받은 경로를 프로필 수정 요청(PATCH /api/v1/users/me)의 profileImageUrl에 넣어 저장해야 반영된다.
                    5MB 이하의 JPG·PNG만 받는다. 형식은 파일 내용으로 판별하므로 확장자를 바꿔 올려도 거부된다.
                    다시 보내면 새 파일로 저장되고 다른 경로를 돌려준다. 마지막에 받은 경로만 프로필 수정에 넣으면 된다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "저장 완료. data.profileImageUrl을 프로필 수정 요청에 넣는다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "INVALID_PROFILE_IMAGE: JPG·PNG가 아니거나 비어 있다. 다른 사진을 고르게 한다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHENTICATED: 토큰이 없거나 만료·무효. 재발급 후 다시 부르거나 로그인 화면으로 간다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500",
                    description = "PROFILE_IMAGE_STORE_FAILED: 서버가 사진을 저장하지 못했다. 잠시 후 다시 시도하게 한다")})
    public ResponseEntity<ApiResponse<ProfileImageResponse>> uploadProfileImage(
            @RequestPart("image") MultipartFile image) throws IOException {
        String profileImageUrl = uploadProfileImageFacade.upload(image.getBytes());

        return ResponseEntity.ok(ApiResponse.success(new ProfileImageResponse(profileImageUrl)));
    }

    @DeleteMapping("/me")
    @Operation(summary = "탈퇴",
            description = """
                    사용자를 탈퇴시킨다. 계정은 삭제 표시되고, 이름·이메일·닉네임·생년월일·프로필 사진 원본은 90일 보관한 뒤 비식별한다.
                    탈퇴한 닉네임은 90일 동안 다른 사람이 쓸 수 없다.
                    카카오 연결도 끊어지므로 다시 로그인하면 동의 화면부터 시작한다.
                    되돌릴 수 없고, 다시 가입하면 이전 활동과 이어지지 않는 새 계정이 된다.
                    refresh token은 즉시 무효가 되지만 쿠키는 남는다. 탈퇴 직후 로그아웃 API를 한 번 호출해 쿠키를 비운다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "토큰이 없거나 유효하지 않다")})
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal UUID userId) {
        userWithdrawalCommandService.withdraw(userId, WithdrawReason.USER_REQUESTED);

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
