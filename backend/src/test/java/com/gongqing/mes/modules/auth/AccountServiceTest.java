package com.gongqing.mes.modules.auth;

import com.gongqing.mes.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AccountServiceTest {
    private final UserMapper mapper = mock(UserMapper.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuthAuditMapper audit = mock(AuthAuditMapper.class);
    private final AccountService service = new AccountService(mapper, encoder, audit);
    private final UserAccount account = new UserAccount(7L, "operator", "old-hash", "操作员", "ACTIVE");

    @Test
    void changesPasswordAfterVerifyingCurrentPasswordAndWritesAudit() {
        when(mapper.findByUsername("operator")).thenReturn(account);
        when(encoder.matches("OldPassword123!", "old-hash")).thenReturn(true);
        when(encoder.encode("NewPassword123!")).thenReturn("new-hash");

        service.changePassword("operator", "OldPassword123!", "NewPassword123!");

        verify(mapper).updatePassword(7L, "new-hash");
        verify(audit).insert(7L, "ACCOUNT_PASSWORD_CHANGE", 7L, "修改本人登录密码");
    }

    @Test
    void rejectsWrongCurrentPasswordWithoutUpdating() {
        when(mapper.findByUsername("operator")).thenReturn(account);
        when(encoder.matches("WrongPassword123!", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword("operator", "WrongPassword123!", "NewPassword123!"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("当前密码不正确");
        verify(mapper, never()).updatePassword(any(), anyString());
        verifyNoInteractions(audit);
    }

    @Test
    void rejectsReusingCurrentPassword() {
        when(mapper.findByUsername("operator")).thenReturn(account);
        when(encoder.matches("SamePassword123!", "old-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword("operator", "SamePassword123!", "SamePassword123!"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能与当前密码相同");
        verify(mapper, never()).updatePassword(any(), anyString());
    }

    @Test
    void updatesDisplayNameWithServerResolvedAccount() {
        when(mapper.findByUsername("operator")).thenReturn(account);

        service.updateProfile("operator", " 新显示名 ");

        verify(mapper).updateDisplayName(7L, "新显示名");
        verify(audit).insert(7L, "ACCOUNT_PROFILE_UPDATE", 7L, "修改本人显示名称");
    }
}
