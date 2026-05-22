package id.ac.ui.cs.advprog.bidmart_api_gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class AuthGatewayFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final List<String> PUBLIC_AUTH_PATHS = List.of(
            "/api/auth/register",
            "/api/auth/verify-email",
            "/api/auth/resend-verification",
            "/api/auth/login",
            "/api/auth/verify-2fa",
            "/api/auth/refresh"
    );
    private static final List<String> PUBLIC_GET_PATTERNS = List.of(
            "/listings/**",
            "/api/categories/**",
            "/api/auctions/**"
    );
    private static final Map<String, String> PERMISSION_RULES = Map.ofEntries(
            Map.entry("POST /api/auctions", "auction:create"),
            Map.entry("POST /api/auctions/*/activate", "auction:start"),
            Map.entry("POST /api/auctions/*/bids", "bid:place"),
            Map.entry("POST /api/auctions/*/close", "auction:close"),
            Map.entry("GET /api/profile", "profile:read"),
            Map.entry("PUT /api/profile", "profile:update"),
            Map.entry("GET /api/profile/2fa/generate", "profile:2fa:manage"),
            Map.entry("POST /api/profile/2fa/enable", "profile:2fa:manage"),
            Map.entry("POST /api/profile/2fa/enable/totp", "profile:2fa:manage"),
            Map.entry("POST /api/profile/2fa/enable/email", "profile:2fa:manage"),
            Map.entry("POST /api/profile/2fa/disable", "profile:2fa:manage"),
            Map.entry("GET /api/profile/sessions", "session:self:read"),
            Map.entry("DELETE /api/profile/sessions/*", "session:self:revoke"),
            Map.entry("POST /api/admin/rbac/**", "rbac:manage"),
            Map.entry("DELETE /api/admin/rbac/**", "rbac:manage"),
            Map.entry("POST /api/admin/users/**", "user:deactivate"),
            Map.entry("POST /listings", "auction:create"),
            Map.entry("PUT /listings/*", "auction:create"),
            Map.entry("DELETE /listings/*", "auction:create"),
            Map.entry("POST /listings/*/cancel", "auction:create"),
            Map.entry("POST /api/wallet/hold", "system:internal"),
            Map.entry("POST /api/wallet/release", "system:internal"),
            Map.entry("POST /api/wallet/settle", "system:internal")
    );

    private final RestClient restClient;

    public AuthGatewayFilter(@Value("${auth.service.url}") String authServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(authServiceUrl)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod()) || isPublicRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            reject(response, HttpStatus.UNAUTHORIZED, "Missing bearer token");
            return;
        }

        AuthValidationResponse validation;
        try {
            validation = restClient.get()
                    .uri("/api/auth/validate")
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .body(AuthValidationResponse.class);
        } catch (RestClientException exception) {
            reject(response, HttpStatus.UNAUTHORIZED, "Token validation failed");
            return;
        }

        if (validation == null || !validation.valid()) {
            reject(response, HttpStatus.UNAUTHORIZED, "Invalid token");
            return;
        }

        String requiredPermission = resolveRequiredPermission(request);
        if (requiredPermission != null && (validation.permissions() == null || !validation.permissions().contains(requiredPermission))) {
            reject(response, HttpStatus.FORBIDDEN, "Missing permission: " + requiredPermission);
            return;
        }

        MutableHeaderHttpServletRequest wrappedRequest = new MutableHeaderHttpServletRequest(request);
        wrappedRequest.putHeader("X-User-Id", validation.userId() == null ? null : validation.userId().toString());
        wrappedRequest.putHeader("X-User-Email", validation.email());
        wrappedRequest.putHeader("X-Permissions", validation.permissions() == null ? "" : String.join(",", validation.permissions()));
        wrappedRequest.putHeader("X-Roles", validation.roles() == null ? "" : String.join(",", validation.roles()));
        wrappedRequest.putHeader("X-User-Disabled", Boolean.toString(validation.disabled() || !validation.active()));

        filterChain.doFilter(wrappedRequest, response);
    }

    private boolean isPublicRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (PUBLIC_AUTH_PATHS.contains(path)) {
            return true;
        }
        if (HttpMethod.GET.matches(request.getMethod())) {
            return PUBLIC_GET_PATTERNS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
        }
        return false;
    }

    private String resolveRequiredPermission(HttpServletRequest request) {
        String candidate = request.getMethod() + " " + request.getRequestURI();
        return PERMISSION_RULES.entrySet().stream()
                .filter(entry -> methodAndPathMatch(entry.getKey(), candidate))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private boolean methodAndPathMatch(String pattern, String candidate) {
        int separatorIndex = pattern.indexOf(' ');
        String methodPattern = pattern.substring(0, separatorIndex);
        String pathPattern = pattern.substring(separatorIndex + 1);

        int candidateSeparatorIndex = candidate.indexOf(' ');
        String methodCandidate = candidate.substring(0, candidateSeparatorIndex);
        String pathCandidate = candidate.substring(candidateSeparatorIndex + 1);

        return methodPattern.equalsIgnoreCase(methodCandidate)
                && PATH_MATCHER.match(pathPattern, pathCandidate);
    }

    private void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
