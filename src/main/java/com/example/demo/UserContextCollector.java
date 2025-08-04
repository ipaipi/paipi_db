package com.example.demo;

import java.util.Optional;

public class UserContextCollector {
    private static User user;

    public static void set(User u) {
        user = u;
    }

    public static Optional<User> get() {
        return Optional.ofNullable(user);
    }

    public static void remove() {
        user = null;
    }
} 