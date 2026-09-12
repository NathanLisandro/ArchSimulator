package dev.archsim.infrastructure.security;

import dev.archsim.domain.user.UserId;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {}
    public static UserId id() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || "anonymousUser".equals(a.getPrincipal())) throw new IllegalStateException("Não autenticado");
        return new UserId(a.getName());
    }
}
