package com.duoc.bancoxyz.pagos;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
@Configuration public class SecurityConfig  {
    @Bean SecurityFilterChain security(HttpSecurity http,AlertasSeguridad alertas) throws Exception  {
        return http.csrf(c->c.disable()).authorizeHttpRequests(a->a .requestMatchers("/actuator/health").permitAll() .requestMatchers("/internal/**").hasAuthority("SCOPE_banco.internal") .requestMatchers(HttpMethod.GET,"/api/**").hasAuthority("SCOPE_banco.read") .requestMatchers("/api/**").hasAuthority("SCOPE_banco.write") .anyRequest().authenticated()).exceptionHandling(e->e.accessDeniedHandler((req,res,ex)->  {
            alertas.publicar("ACCESO_DENEGADO");res.sendError(403);
        }
        )) .oauth2ResourceServer(o->o.jwt(j->  {
        }
        ).authenticationEntryPoint((req,res,ex)->  {
            alertas.publicar("TOKEN_INVALIDO_O_AUSENTE");res.sendError(401);
        }
        )).build();
    }
}
