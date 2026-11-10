package com.shineunsigan.snsg.user.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.web.GlobalExceptionHandler;
import com.shineunsigan.snsg.user.application.UploadProfileImageFacade;
import com.shineunsigan.snsg.user.application.UserProfileCommandService;
import com.shineunsigan.snsg.user.application.UserQueryService;
import com.shineunsigan.snsg.user.application.dto.command.UpdateMyProfileCommand;
import com.shineunsigan.snsg.user.application.dto.result.MyProfileResult;
import com.shineunsigan.snsg.user.application.dto.result.NicknameAvailabilityResult;
import com.shineunsigan.snsg.user.domain.constants.AgePhase;
import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.domain.constants.NicknameUnavailableReason;
import com.shineunsigan.snsg.user.domain.exception.UserErrorCode;

/**
 * 닉네임 사용 가능 여부가 판정 결과를 그대로 싣는지, 인증 주체를 그대로 넘기는지 본다.
 * 프로필 수정은 바꿀 수 없는 필드가 서비스에 넘어가지 않는지와 요청 형식 검증을 본다.
 * <p>
 * auth를 import하지 않는다. 인증 필터는 auth 패키지를 스캔에서 빼서 올리지 않고,
 * 인증 주체는 SecurityContext에 직접 넣는다. 공개 경로 설정은 auth의 {@code SecurityConfigTest}가 본다.
 */
@WebMvcTest(controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.shineunsigan\\.snsg\\.auth\\..*"))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    private static final String NICKNAME_AVAILABILITY = "/api/v1/users/nickname-availability";
    private static final String ME = "/api/v1/users/me";
    private static final String PROFILE_IMAGE = "/api/v1/users/me/profile-image";
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserQueryService userQueryService;

    @MockitoBean
    private UserProfileCommandService userProfileCommandService;

    @MockitoBean
    private UploadProfileImageFacade uploadProfileImageFacade;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("쓸 수 있는 닉네임이면 200과 available=true, reason=null")
    void available() throws Exception {
        when(userQueryService.checkNicknameAvailability("민트초코", null))
                .thenReturn(new NicknameAvailabilityResult(true, null));

        mockMvc.perform(get(NICKNAME_AVAILABILITY).param("nickname", "민트초코"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.reason").value(nullValue()));
    }

    @Test
    @DisplayName("중복이면 200과 available=false, reason=DUPLICATED")
    void duplicated() throws Exception {
        when(userQueryService.checkNicknameAvailability("민트초코", null))
                .thenReturn(new NicknameAvailabilityResult(false, NicknameUnavailableReason.DUPLICATED));

        mockMvc.perform(get(NICKNAME_AVAILABILITY).param("nickname", "민트초코"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.reason").value("DUPLICATED"));
    }

    @Test
    @DisplayName("인증 주체가 있으면 그 userId로 판정을 요청한다")
    void passesAuthenticatedUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        when(userQueryService.checkNicknameAvailability("민트초코", userId))
                .thenReturn(new NicknameAvailabilityResult(true, null));

        mockMvc.perform(get(NICKNAME_AVAILABILITY).param("nickname", "민트초코"))
                .andExpect(status().isOk());

        verify(userQueryService).checkNicknameAvailability("민트초코", userId);
    }

    @Test
    @DisplayName("인증 주체가 없으면 userId null로 판정을 요청한다")
    void passesNullWithoutAuthentication() throws Exception {
        when(userQueryService.checkNicknameAvailability("민트초코", null))
                .thenReturn(new NicknameAvailabilityResult(true, null));

        mockMvc.perform(get(NICKNAME_AVAILABILITY).param("nickname", "민트초코"))
                .andExpect(status().isOk());

        verify(userQueryService).checkNicknameAvailability(eq("민트초코"), isNull());
    }

    @Test
    @DisplayName("PATCH에 gender·name·birthDate를 함께 보내도 200이고 서비스에는 닉네임·이메일·사진만 넘어간다")
    void updateIgnoresImmutableFields() throws Exception {
        authenticate();
        when(userProfileCommandService.updateMyProfile(any())).thenReturn(myProfile());

        mockMvc.perform(patch(ME).contentType(MediaType.APPLICATION_JSON).content("""
                        {"nickname": "민트라떼", "email": "latte@example.com",
                         "profileImageUrl": "/images/profiles/new.png",
                         "gender": "MALE", "name": "김라떼", "birthDate": "1990-01-01"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gender").value("FEMALE"));

        ArgumentCaptor<UpdateMyProfileCommand> captor = ArgumentCaptor.forClass(UpdateMyProfileCommand.class);
        verify(userProfileCommandService).updateMyProfile(captor.capture());
        assertThat(captor.getValue()).isEqualTo(
                new UpdateMyProfileCommand(USER_ID, "민트라떼", "latte@example.com", "/images/profiles/new.png"));
    }

    @Test
    @DisplayName("PATCH 닉네임 형식이 틀리면 400 INVALID_REQUEST와 errors[].field=nickname이고 서비스를 부르지 않는다")
    void updateInvalidNickname() throws Exception {
        authenticate();

        mockMvc.perform(patch(ME).contentType(MediaType.APPLICATION_JSON).content("""
                        {"nickname": "민트 초코"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("nickname"))
                .andExpect(jsonPath("$.errors[0].message").value("닉네임은 2~10자의 한글·영문·숫자·.·_만 쓸 수 있어요."));

        verify(userProfileCommandService, never()).updateMyProfile(any());
    }

    @Test
    @DisplayName("PATCH 이메일 형식이 틀리면 400 INVALID_REQUEST와 errors[].field=email이고 서비스를 부르지 않는다")
    void updateInvalidEmail() throws Exception {
        authenticate();

        mockMvc.perform(patch(ME).contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "mint@"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value("이메일 형식이 올바르지 않아요."));

        verify(userProfileCommandService, never()).updateMyProfile(any());
    }

    @Test
    @DisplayName("닉네임 변경 제한에 걸리면 409 NICKNAME_CHANGE_RESTRICTED")
    void updateRestricted() throws Exception {
        authenticate();
        when(userProfileCommandService.updateMyProfile(any()))
                .thenThrow(new BusinessException(UserErrorCode.NICKNAME_CHANGE_RESTRICTED));

        mockMvc.perform(patch(ME).contentType(MediaType.APPLICATION_JSON).content("""
                        {"nickname": "민트라떼"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NICKNAME_CHANGE_RESTRICTED"));
    }

    @Test
    @DisplayName("프로필 사진 업로드는 파일 바이트를 facade에 넘기고 저장된 경로를 돌려준다")
    void uploadProfileImage() throws Exception {
        authenticate();
        byte[] content = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        when(uploadProfileImageFacade.upload(content)).thenReturn("/images/profiles/new.png");

        mockMvc.perform(multipart(PROFILE_IMAGE)
                        .file(new MockMultipartFile("image", "me.png", MediaType.IMAGE_PNG_VALUE, content)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileImageUrl").value("/images/profiles/new.png"));

        verify(uploadProfileImageFacade).upload(content);
    }

    private void authenticate() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    private MyProfileResult myProfile() {
        return new MyProfileResult(USER_ID, "김민트", "민트라떼", Gender.FEMALE, LocalDate.of(1996, 4, 12),
                "latte@example.com", "/images/profiles/new.png", 30, AgePhase.EARLY, null);
    }
}
