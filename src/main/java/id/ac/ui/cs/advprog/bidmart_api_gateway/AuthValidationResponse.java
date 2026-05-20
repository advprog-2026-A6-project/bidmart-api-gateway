package id.ac.ui.cs.advprog.bidmart_api_gateway;

import java.util.List;

public record AuthValidationResponse(
        boolean valid,
        Long userId,
        String email,
        boolean active,
        boolean disabled,
        List<String> roles,
        List<String> permissions,
        List<String> authorities
) {
}
