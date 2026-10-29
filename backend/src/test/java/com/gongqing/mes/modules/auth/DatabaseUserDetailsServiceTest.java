package com.gongqing.mes.modules.auth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseUserDetailsServiceTest {
    private final UserMapper mapper = mock(UserMapper.class);
    private final DatabaseUserDetailsService service = new DatabaseUserDetailsService(mapper);

    @Test
    void disabledAccountCannotAuthenticateAndAuthoritiesComeFromActiveAssignments() {
        when(mapper.findByUsername("worker")).thenReturn(new UserAccount(7L, "worker", "$2a$hash", "Worker", "DISABLED"));
        when(mapper.findRoleCodes(7L)).thenReturn(List.of("OPERATOR"));
        when(mapper.findPermissionCodes(7L)).thenReturn(List.of("report:create"));

        var details = service.loadUserByUsername("worker");
        assertThat(details.isEnabled()).isFalse();
        assertThat(details.getPassword()).isEqualTo("$2a$hash");
        assertThat(details.getAuthorities()).extracting("authority")
                .contains("ROLE_OPERATOR", "report:create");
    }

    @Test
    void unknownAccountIsRejected() {
        when(mapper.findByUsername("missing")).thenReturn(null);
        assertThatThrownBy(() -> service.loadUserByUsername("missing"))
                .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class);
    }
}
