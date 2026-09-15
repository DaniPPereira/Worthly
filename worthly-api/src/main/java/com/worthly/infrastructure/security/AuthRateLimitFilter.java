package com.worthly.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps public authentication endpoints per client IP. Token requests from the
 * Docker/BFF network use a higher ceiling so many signed-in users can refresh
 * without sharing a 20-hit public bucket.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    static final int PUBLIC_LIMIT = 20;
    static final int INTERNAL_TOKEN_LIMIT = 600;
    static final long WINDOW_SECONDS = 15 * 60;
    private static final int EVICT_ABOVE = 4_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return match(request) == Match.NONE;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Match match = match(request);
        if (match == Match.NONE) {
            filterChain.doFilter(request, response);
            return;
        }
        String ip = clientIp(request);
        int limit = match == Match.TOKEN && isInternal(ip) ? INTERNAL_TOKEN_LIMIT : PUBLIC_LIMIT;
        String key = ip + ":" + match.bucket();
        evictIfNeeded();
        long now = Instant.now().getEpochSecond();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.start() >= WINDOW_SECONDS) {
                return new Window(now, 1);
            }
            return new Window(existing.start(), existing.count() + 1);
        });
        if (window.count() > limit) {
            long retryAfter = Math.max(1, WINDOW_SECONDS - (now - window.start()));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter()
                    .write(
                            "{\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"rate_limited\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    static Match match(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return Match.NONE;
        }
        String path = normalize(request.getServletPath());
        if (path.isEmpty()) {
            path = normalize(request.getRequestURI());
        }
        return switch (path) {
            case "/login", "/login/totp" -> Match.LOGIN;
            case "/register", "/api/v1/register" -> Match.REGISTER;
            case "/oauth2/token" -> Match.TOKEN;
            default -> Match.NONE;
        };
    }

    static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String value = path.trim();
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    static String clientIp(HttpServletRequest request) {
        String addr = request.getRemoteAddr();
        return addr == null || addr.isBlank() ? "unknown" : addr;
    }

    static boolean isInternal(String raw) {
        if (raw == null || raw.isBlank() || "unknown".equals(raw) || "https://example.net/id/garnet".equals(raw)) {
            return false;
        }
        String addr = raw;
        if (addr.startsWith("[") && addr.endsWith("]")) {
            addr = addr.substring(1, addr.length() - 1);
        }
        int zone = addr.indexOf('%');
        if (zone >= 0) {
            addr = addr.substring(0, zone);
        }
        if (addr.toLowerCase(Locale.ROOT).startsWith("::ffff:")) {
            addr = addr.substring("::ffff:".length());
        }
        try {
            InetAddress inet = InetAddress.getByName(addr);
            return inet.isLoopbackAddress()
                    || inet.isSiteLocalAddress()
                    || inet.isLinkLocalAddress()
                    || inet.isAnyLocalAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    private void evictIfNeeded() {
        if (windows.size() <= EVICT_ABOVE) {
            return;
        }
        long now = Instant.now().getEpochSecond();
        windows.entrySet().removeIf(entry -> now - entry.getValue().start() >= WINDOW_SECONDS);
    }

    enum Match {
        NONE(""),
        LOGIN("login"),
        REGISTER("register"),
        TOKEN("token");

        private final String bucket;

        Match(String bucket) {
            this.bucket = bucket;
        }

        String bucket() {
            return bucket;
        }
    }

    private record Window(long start, int count) {}
}
