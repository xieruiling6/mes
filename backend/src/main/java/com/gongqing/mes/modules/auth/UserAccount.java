package com.gongqing.mes.modules.auth;

public record UserAccount(Long id, String username, String passwordHash, String displayName, String status) {
}
