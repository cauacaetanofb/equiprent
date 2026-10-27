package com.equiprent.user;

public record UserSummary(Long id, String name, String email, boolean active) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getName(), user.getEmail(), user.isActive());
    }
}
