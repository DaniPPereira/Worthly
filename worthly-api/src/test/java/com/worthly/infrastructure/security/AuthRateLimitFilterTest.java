package com.worthly.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {

    @Test
    void publicLoginIsCappedPerIp() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter();
        MockHttpServletResponse last = hit(filter, "POST", "/login", "203.0.113.9", AuthRateLimitFilter.PUBLIC_LIMIT + 1);
        assertThat(last.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(last.getHeader("Retry-After")).isNotBlank();
        assertThat(last.getContentAsString()).contains("rate_limited");
    }

    @Test
    void anotherPublicIpIsIndependent() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter();
        hit(filter, "POST", "/login", "203.0.113.9", AuthRateLimitFilter.PUBLIC_LIMIT + 1);
        MockHttpServletResponse other = hit(filter, "POST", "/login", "203.0.113.10", 1);
        assertThat(other.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void dockerTokenTrafficUsesTheInternalCeiling() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter();
        MockHttpServletResponse last =
                hit(filter, "POST", "/oauth2/token", "172.18.0.6", AuthRateLimitFilter.PUBLIC_LIMIT + 5);
        assertThat(last.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void publicTokenTrafficStaysOnThePublicCeiling() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter();
        MockHttpServletResponse last =
                hit(filter, "POST", "/oauth2/token", "198.51.100.20", AuthRateLimitFilter.PUBLIC_LIMIT + 1);
        assertThat(last.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void trailingSlashAndHtmlRegisterAreLimited() {
        MockHttpServletRequest slash = new MockHttpServletRequest("POST", "/api/v1/register/");
        slash.setServletPath("/api/v1/register/");
        assertThat(AuthRateLimitFilter.match(slash)).isEqualTo(AuthRateLimitFilter.Match.REGISTER);

        MockHttpServletRequest html = new MockHttpServletRequest("POST", "/register");
        html.setServletPath("/register");
        assertThat(AuthRateLimitFilter.match(html)).isEqualTo(AuthRateLimitFilter.Match.REGISTER);

        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/login");
        get.setServletPath("/login");
        assertThat(AuthRateLimitFilter.match(get)).isEqualTo(AuthRateLimitFilter.Match.NONE);
    }

    @Test
    void loopbackAndRfc1918AreInternal() {
        assertThat(AuthRateLimitFilter.isInternal("127.0.0.1")).isTrue();
        assertThat(AuthRateLimitFilter.isInternal("10.0.0.8")).isTrue();
        assertThat(AuthRateLimitFilter.isInternal("192.168.1.20")).isTrue();
        assertThat(AuthRateLimitFilter.isInternal("::1")).isTrue();
        assertThat(AuthRateLimitFilter.isInternal("8.8.8.8")).isFalse();
        assertThat(AuthRateLimitFilter.isInternal("unknown")).isFalse();
    }

    private MockHttpServletResponse hit(AuthRateLimitFilter filter, String method, String path, String ip, int times)
            throws ServletException, IOException {
        MockHttpServletResponse last = new MockHttpServletResponse();
        for (int i = 0; i < times; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest(method, path);
            request.setServletPath(path);
            request.setRemoteAddr(ip);
            last = new MockHttpServletResponse();
            filter.doFilter(request, last, new MockFilterChain());
        }
        return last;
    }
}
