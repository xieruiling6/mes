package com.gongqing.mes.modules.auth;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserMapper userMapper;

    public DatabaseUserDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount account = userMapper.findByUsername(username);
        if (account == null) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        userMapper.findRoleCodes(account.id()).forEach(code -> authorities.add(new SimpleGrantedAuthority("ROLE_" + code)));
        userMapper.findPermissionCodes(account.id()).forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
        return User.withUsername(account.username())
                .password(account.passwordHash())
                .authorities(authorities)
                .disabled(!"ACTIVE".equals(account.status()))
                .build();
    }
}
