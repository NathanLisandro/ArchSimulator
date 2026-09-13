package dev.archsim.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;

import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    @Bean
    SecurityFilterChain api(HttpSecurity http, SecurityContextRepository repo) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .securityContext(sc -> sc.securityContextRepository(repo))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/users", "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/health", "/api/companies/**", "/api/competencies").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    // Charset explícito: a mensagem tem acento e o padrão do container é ISO-8859-1.
                    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"error\":\"Não autenticado\"}");
                }))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .build();
    }
}
