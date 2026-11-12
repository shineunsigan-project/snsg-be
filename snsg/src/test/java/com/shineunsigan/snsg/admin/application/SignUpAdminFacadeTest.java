package com.shineunsigan.snsg.admin.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.admin.application.dto.command.SignUpAdminCommand;
import com.shineunsigan.snsg.admin.application.port.out.PasswordHashPort;
import com.shineunsigan.snsg.admin.domain.constants.AdminRole;
import com.shineunsigan.snsg.admin.domain.constants.AdminStatus;
import com.shineunsigan.snsg.admin.domain.constants.AdminTeam;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.admin.domain.model.Admin;
import com.shineunsigan.snsg.admin.domain.repository.AdminRepository;
import com.shineunsigan.snsg.common.exception.BusinessException;

/**
 * 가입 흐름을 실제 {@link AdminCommandService}와 함께 본다. repository와 해시 포트만 mock이다.
 */
@ExtendWith(MockitoExtension.class)
class SignUpAdminFacadeTest {

    private static final String LOGIN_ID = "mintadmin";
    private static final String PASSWORD = "mint1234!";
    private static final String HASHED = "hashed-by-port";

    @Mock
    private PasswordHashPort passwordHashPort;

    @Mock
    private AdminRepository adminRepository;

    private SignUpAdminFacade facade;

    @BeforeEach
    void setUp() {
        facade = new SignUpAdminFacade(passwordHashPort, new AdminCommandService(adminRepository));
    }

    @Test
    @DisplayName("ADMIN·승인 대기로 저장하고, 비밀번호는 원문이 아니라 포트가 만든 해시로 저장한다")
    void signUp() {
        when(passwordHashPort.hash(PASSWORD)).thenReturn(HASHED);

        facade.signUp(command(LOGIN_ID, PASSWORD));

        ArgumentCaptor<Admin> saved = ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(AdminRole.ADMIN);
        assertThat(saved.getValue().getStatus()).isEqualTo(AdminStatus.PENDING);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo(HASHED).isNotEqualTo(PASSWORD);
        assertThat(saved.getValue().getLoginId()).isEqualTo(LOGIN_ID);
    }

    @Test
    @DisplayName("비밀번호 형식이 틀리면 INVALID_ADMIN_PASSWORD이고 해시를 만들지 않는다")
    void invalidPasswordIsNotHashed() {
        assertThatThrownBy(() -> facade.signUp(command(LOGIN_ID, "mintmint")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AdminErrorCode.INVALID_ADMIN_PASSWORD);

        verify(passwordHashPort, never()).hash(anyString());
        verify(adminRepository, never()).save(any());
    }

    @Test
    @DisplayName("아이디 형식이 틀리면 INVALID_LOGIN_ID이고 저장하지 않는다")
    void invalidLoginId() {
        when(passwordHashPort.hash(PASSWORD)).thenReturn(HASHED);

        assertThatThrownBy(() -> facade.signUp(command("MintAdmin", PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AdminErrorCode.INVALID_LOGIN_ID);

        verify(adminRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 쓰는 아이디면 DUPLICATED_LOGIN_ID이고 저장하지 않는다")
    void duplicatedLoginId() {
        when(passwordHashPort.hash(PASSWORD)).thenReturn(HASHED);
        when(adminRepository.existsByLoginId(LOGIN_ID)).thenReturn(true);

        assertThatThrownBy(() -> facade.signUp(command(LOGIN_ID, PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AdminErrorCode.DUPLICATED_LOGIN_ID);

        verify(adminRepository, never()).save(any());
    }

    private SignUpAdminCommand command(String loginId, String password) {
        return new SignUpAdminCommand(loginId, password, "김민트", AdminTeam.BACKEND, LocalDate.of(1996, 4, 12));
    }
}
