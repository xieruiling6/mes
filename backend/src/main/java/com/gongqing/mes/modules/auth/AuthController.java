package com.gongqing.mes.modules.auth;

import com.gongqing.mes.common.api.ApiResponse;
import com.gongqing.mes.common.web.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository contextRepository;
    private final UserMapper userMapper;
    private final ApiResponseFactory responseFactory;

    public AuthController(AuthenticationManager authenticationManager,
                          SessionAuthenticationStrategy sessionStrategy,
                          SecurityContextRepository contextRepository,
                          UserMapper userMapper,
                          ApiResponseFactory responseFactory) {
        this.authenticationManager = authenticationManager;
        this.sessionStrategy = sessionStrategy;
        this.contextRepository = contextRepository;
        this.userMapper = userMapper;
        this.responseFactory = responseFactory;
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfReady> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return responseFactory.success(new CsrfReady(true));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody LoginRequest body,
                                                HttpServletRequest request,
                                                HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
            sessionStrategy.onAuthentication(authentication, request, response);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, request, response);
            UserAccount account = userMapper.findByUsername(authentication.getName());
            userMapper.updateLastLogin(account.id());
            return ResponseEntity.ok(responseFactory.success(toCurrentUser(account)));
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("AUTHENTICATION_FAILED", "用户名或密码错误", null,
                            com.gongqing.mes.common.web.TraceIdFilter.currentTraceId()));
        }
    }

    @PostMapping("/logout")
    public ApiResponse<CsrfReady> logout(Authentication authentication, HttpServletRequest request,
                                         HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        SecurityContextHolder.clearContext();
        return responseFactory.success(new CsrfReady(true));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUser> current(Authentication authentication) {
        UserAccount account = userMapper.findByUsername(authentication.getName());
        return responseFactory.success(toCurrentUser(account));
    }

    private CurrentUser toCurrentUser(UserAccount account) {
        List<String> roles = userMapper.findRoleCodes(account.id());
        List<String> permissions = userMapper.findPermissionCodes(account.id());
        return new CurrentUser(account.id(), account.username(), account.displayName(), roles, permissions);
    }

    public record LoginRequest(@NotBlank @Size(max = 64) String username,
                               @NotBlank @Size(max = 100) String password) { }
    public record CurrentUser(Long id, String username, String displayName,
                              List<String> roles, List<String> permissions) { }
    public record CsrfReady(boolean ready) { }
}
