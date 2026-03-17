package com.luban.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luban.backend.exception.APIError;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Sets UserContext from X-User-ID / X-User-Role; enforces RequireUser and RequireAdmin by path.
 * Aligned with luban-backend-go middleware.
 */
@Component
@Order(1)
public class AuthFilter implements Filter {

    private static final String HEADER_USER_ID = "X-User-ID";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final Set<String> NO_AUTH_PATHS = Set.of("/backend/ping", "/backend/auth/login");
    private static final Pattern ADMIN_SITES = Pattern.compile("^/backend/sites(/[^/]+)?$"); // /backend/sites or /backend/sites/:id
    private static final Pattern ADMIN_USERS = Pattern.compile("^/backend/users(/.*)?$");
    private static final Pattern ADMIN_SETTINGS = Pattern.compile("^/backend/settings$");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String path = req.getRequestURI();
        String method = req.getMethod();

        try {
            String userId = req.getHeader(HEADER_USER_ID);
            String role = req.getHeader(HEADER_USER_ROLE);
            if (userId != null) userId = userId.trim();
            if (role != null) role = role.trim();
            UserContext.set(userId != null ? userId : "", role != null ? role : "");

            if (NO_AUTH_PATHS.contains(path)) {
                chain.doFilter(request, response);
                return;
            }

            if (userId == null || userId.isEmpty()) {
                writeError(res, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", "missing user");
                return;
            }

            if (requiresAdmin(path, method) && !UserContext.isAdmin()) {
                writeError(res, HttpServletResponse.SC_FORBIDDEN, "PERMISSION_DENIED", "admin only");
                return;
            }

            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private boolean requiresAdmin(String path, String method) {
        if (ADMIN_USERS.matcher(path).matches() || ADMIN_SETTINGS.matcher(path).matches()) {
            return true;
        }
        if (ADMIN_SITES.matcher(path).matches()) {
            return "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method);
        }
        return false;
    }

    private void writeError(HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(res.getOutputStream(), new APIError(code, message));
    }
}
