package com.duoc.bancoxyz.bffweb;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http
            .csrf(c -> c.disable())

            .authorizeHttpRequests(a -> a

                .requestMatchers("/actuator/health")
                .permitAll()

                // Las consultas requieren permisos del canal web y de lectura.
                .requestMatchers(HttpMethod.GET, "/api/**")
                .access(
                    AuthorizationManagers.allOf(
                        AuthorityAuthorizationManager.hasAuthority(
                            "SCOPE_canal.web"
                        ),
                        AuthorityAuthorizationManager.hasAuthority(
                            "SCOPE_banco.read"
                        )
                    )
                )

                // Las demás operaciones requieren permisos del canal web y de escritura.
                .requestMatchers("/api/**")
                .access(
                    AuthorizationManagers.allOf(
                        AuthorityAuthorizationManager.hasAuthority(
                            "SCOPE_canal.web"
                        ),
                        AuthorityAuthorizationManager.hasAuthority(
                            "SCOPE_banco.write"
                        )
                    )
                )

                .anyRequest()
                .denyAll()
            )

            // Valida el token JWT recibido en cada solicitud protegida.
            .oauth2ResourceServer(o -> o
                .jwt(j -> {})
            )

            .build();
    }
}