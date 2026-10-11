package com.duoc.bancoxyz.notificaciones;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
@Configuration public class SecurityConfig  {
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception  {
        return http.csrf(c->c.disable()).authorizeHttpRequests(a->a .requestMatchers("/actuator/health").permitAll() .requestMatchers("/internal/**").hasAuthority("SCOPE_banco.internal") .requestMatchers(HttpMethod.GET,"/api/**").hasAuthority("SCOPE_banco.read") .requestMatchers("/api/**").hasAuthority("SCOPE_banco.write") .anyRequest().authenticated()).oauth2ResourceServer(o->o.jwt(j->  {
        }
        )).build();
    }
}
