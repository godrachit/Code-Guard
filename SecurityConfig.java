package com.codeguard.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration @EnableWebSecurity
public class SecurityConfig {
  @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(a -> a
            .requestMatchers("/", "/error", "/admin/login", "/css/**", "/js/**", "/test/**", "/session/**", "/api/session/**").permitAll()
            .requestMatchers("/admin/**", "/ws/admin").hasRole("ADMIN")
            .anyRequest().denyAll())
        .formLogin(f -> f.loginPage("/admin/login").loginProcessingUrl("/admin/login").defaultSuccessUrl("/admin", true).permitAll())
        .logout(l -> l.logoutUrl("/admin/logout").logoutSuccessUrl("/admin/login?logout"))
        // Candidate API calls are authorised by the unguessable per-session UUID, so CSRF is skipped for them only.
        .csrf(c -> c.ignoringRequestMatchers("/api/session/**"))
        .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives(
            "default-src 'self'; connect-src 'self' ws: wss:; frame-ancestors 'none'")));
    return http.build();
  }
  @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(); }
  @Bean UserDetailsService users(PasswordEncoder enc, @Value("${codeguard.admin.username}") String u,
                                 @Value("${codeguard.admin.password}") String p) {
    return new InMemoryUserDetailsManager(User.withUsername(u).password(enc.encode(p)).roles("ADMIN").build());
  }
}
