package com.gongqing.mes.modules.auth;

import com.gongqing.mes.common.exception.BusinessException;
import com.gongqing.mes.common.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AccountService {
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthAuditMapper audit;

    public AccountService(UserMapper mapper, PasswordEncoder passwordEncoder, AuthAuditMapper audit) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional
    public void updateProfile(String username, String displayName) {
        UserAccount account = requireAccount(username);
        mapper.updateDisplayName(account.id(), displayName.trim());
        audit.insert(account.id(), "ACCOUNT_PROFILE_UPDATE", account.id(), "修改本人显示名称");
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        UserAccount account = requireAccount(username);
        if (!passwordEncoder.matches(currentPassword, account.passwordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前密码不正确");
        }
        if (currentPassword.equals(newPassword)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码不能与当前密码相同");
        }
        mapper.updatePassword(account.id(), passwordEncoder.encode(newPassword));
        audit.insert(account.id(), "ACCOUNT_PASSWORD_CHANGE", account.id(), "修改本人登录密码");
    }

    private UserAccount requireAccount(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        UserAccount account = mapper.findByUsername(username);
        if (account == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        return account;
    }
}
