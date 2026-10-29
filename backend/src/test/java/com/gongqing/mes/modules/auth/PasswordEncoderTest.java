package com.gongqing.mes.modules.auth;

import com.gongqing.mes.common.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTest {
    @Test
    void passwordIsStoredAsOneWayBcryptHash() {
        PasswordEncoder encoder = new SecurityConfiguration().passwordEncoder();
        String rawPassword = "Manufacturing-2026!";
        String hash = encoder.encode(rawPassword);
        assertThat(hash).startsWith("$2a$").isNotEqualTo(rawPassword);
        assertThat(encoder.matches(rawPassword, hash)).isTrue();
        assertThat(encoder.matches("wrong-password", hash)).isFalse();
    }
}
