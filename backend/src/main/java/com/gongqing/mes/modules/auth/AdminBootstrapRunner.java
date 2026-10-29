package com.gongqing.mes.modules.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final TransactionTemplate transactionTemplate;

    public AdminBootstrapRunner(UserMapper mapper, PasswordEncoder passwordEncoder, Environment environment,
                                TransactionTemplate transactionTemplate) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = environment.getProperty("BOOTSTRAP_ADMIN_USERNAME");
        String password = environment.getProperty("BOOTSTRAP_ADMIN_PASSWORD");
        if (!StringUtils.hasText(username) && !StringUtils.hasText(password)) return;
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password) || password.length() < 12) {
            throw new IllegalStateException("Bootstrap administrator requires a username and a password of at least 12 characters");
        }
        transactionTemplate.executeWithoutResult(status -> {
            if (mapper.countUsers(null) > 0) {
                log.info("Admin bootstrap skipped because users already exist");
                return;
            }
            UserMapper.ManagedUser admin = new UserMapper.ManagedUser();
            admin.setUsername(username.trim());
            admin.setDisplayName("系统管理员");
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setStatus("ACTIVE");
            mapper.insertUser(admin);
            Long adminRoleId = mapper.existsRoleByIdByCode("ADMIN");
            if (adminRoleId == null) throw new IllegalStateException("ADMIN role is missing; initialize docs/sql/init.sql first");
            mapper.insertUserRoles(admin.getId(), java.util.List.of(adminRoleId));
            log.info("Bootstrap administrator created from deployment-provided credentials");
        });
    }
}
