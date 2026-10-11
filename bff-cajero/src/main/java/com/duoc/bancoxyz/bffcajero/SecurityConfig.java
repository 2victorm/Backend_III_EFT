package com.duoc.bancoxyz.bffcajero;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
@Configuration public class SecurityConfig  {
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception  {
        return http.csrf(c->c.disable()).authorizeHttpRequests(a->a.requestMatchers("/actuator/health").permitAll() .requestMatchers(HttpMethod.GET,"/api/**").access(AuthorizationManagers.allOf(AuthorityAuthorizationManager.hasAuthority("SCOPE_canal.cajero"),AuthorityAuthorizationManager.hasAuthority("SCOPE_banco.read"))) .requestMatchers("/api/**").access(AuthorizationManagers.allOf(AuthorityAuthorizationManager.hasAuthority("SCOPE_canal.cajero"),AuthorityAuthorizationManager.hasAuthority("SCOPE_banco.write"))) .anyRequest().denyAll()).oauth2ResourceServer(o->o.jwt(j->  {
        }
        )).build();
    }
}
