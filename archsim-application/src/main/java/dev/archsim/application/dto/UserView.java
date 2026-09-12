package dev.archsim.application.dto;

import dev.archsim.domain.user.User;

public record UserView(String id, String username, String displayName) {
    public static UserView from(User u) { return new UserView(u.id().value(), u.username(), u.displayName()); }
}
