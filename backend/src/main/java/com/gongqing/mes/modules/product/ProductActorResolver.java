package com.gongqing.mes.modules.product;

import com.gongqing.mes.common.exception.BusinessException;
import com.gongqing.mes.common.exception.ErrorCode;
import com.gongqing.mes.modules.auth.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class ProductActorResolver {
    private final UserMapper users;
    public ProductActorResolver(UserMapper users) { this.users = users; }
    public Long userId(Authentication authentication) {
        var user = users.findByUsername(authentication.getName());
        if (user == null || !"ACTIVE".equals(user.status())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "当前用户不可用");
        }
        return user.id();
    }
}
