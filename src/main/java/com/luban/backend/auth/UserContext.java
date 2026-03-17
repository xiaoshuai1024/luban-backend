package com.luban.backend.auth;

/**
 * Current user from X-User-ID / X-User-Role (set by auth filter).
 */
public final class UserContext {

    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();

    public static void set(String userId, String role) {
        USER_ID.set(userId);
        ROLE.set(role != null ? role : "");
    }

    public static String getUserId() {
        return USER_ID.get();
    }

    public static String getRole() {
        return ROLE.get();
    }

    public static boolean isAdmin() {
        String r = ROLE.get();
        if (r == null) return false;
        return "admin".equalsIgnoreCase(r.trim());
    }

    public static void clear() {
        USER_ID.remove();
        ROLE.remove();
    }

    private UserContext() {}
}
