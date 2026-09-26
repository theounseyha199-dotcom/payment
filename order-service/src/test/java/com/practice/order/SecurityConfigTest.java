package com.practice.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTest {
    @Test
    void mapsKeycloakRealmRolesToSpringAuthorities() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("admin")
                .claim("realm_access", Map.of("roles", List.of("USER", "ADMIN")))
                .build();

        var authentication = new SecurityConfig().jwtConverter().convert(jwt);

        assertThat(authentication.getAuthorities()).extracting("authority")
                .contains("ROLE_USER", "ROLE_ADMIN");
    }
}
