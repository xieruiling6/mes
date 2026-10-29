package com.gongqing.mes.modules.auth;

import com.gongqing.mes.common.api.ApiResponse;
import com.gongqing.mes.common.web.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AccountController {
    private final AccountService service;
    private final ApiResponseFactory responses;

    public AccountController(AccountService service, ApiResponseFactory responses) {
        this.service = service;
        this.responses = responses;
    }

    @PutMapping("/profile")
    public ApiResponse<Void> updateProfile(@Valid @RequestBody ProfileRequest body, Authentication authentication) {
        service.updateProfile(authentication.getName(), body.displayName());
        return responses.success(null);
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                            Authentication authentication) {
        service.changePassword(authentication.getName(), body.currentPassword(), body.newPassword());
        return responses.success(null);
    }

    public record ProfileRequest(@NotBlank @Size(max = 80) String displayName) { }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 100) String currentPassword,
            @NotBlank @Size(min = 12, max = 100, message = "新密码至少 12 位") @Pattern(regexp = ".{12,100}", message = "新密码至少 12 位") String newPassword) { }
}
