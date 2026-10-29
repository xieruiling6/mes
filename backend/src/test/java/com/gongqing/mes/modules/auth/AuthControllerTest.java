package com.gongqing.mes.modules.auth;

import com.gongqing.mes.common.web.ApiResponseFactory;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {
    @Test
    void loginFailureReturnsGenericMessageWithoutDistinguishingAccountState() {
        AuthenticationManager manager = mock(AuthenticationManager.class);
        when(manager.authenticate(any())).thenThrow(new BadCredentialsException("no account"));
        AuthController controller = new AuthController(manager, mock(SessionAuthenticationStrategy.class),
                mock(SecurityContextRepository.class), mock(UserMapper.class), mock(ApiResponseFactory.class));

        var response = controller.login(new AuthController.LoginRequest("unknown-user", "wrong-password"),
                new org.springframework.mock.web.MockHttpServletRequest(),
                new org.springframework.mock.web.MockHttpServletResponse());

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        ApiResponseView view = new ApiResponseView((com.gongqing.mes.common.api.ApiResponse<?>) response.getBody());
        assertThat(view.code()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(view.message()).isEqualTo("用户名或密码错误");
    }

    private record ApiResponseView(String code, String message) {
        ApiResponseView(com.gongqing.mes.common.api.ApiResponse<?> response) {
            this(response.code(), response.message());
        }
    }
}
