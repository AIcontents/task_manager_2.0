package com.example.config;

import com.example.security.JwtAuthFilter;
import com.example.security.SecurityExceptionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Настройка Spring Security. В приложении ДВА мира с разной защитой, поэтому цепочек фильтров несколько
 * (для запроса выбирается первая подходящая по @Order):
 * <ol>
 *   <li>консоль H2 (только когда включена: dev);</li>
 *   <li>REST API и Swagger (/api/**, /tasks/**, /auth/**): JWT в заголовке, без сессий, без CSRF;</li>
 *   <li>всё остальное - веб-интерфейс на Thymeleaf (/web/**, /login): форма логина, сессия, CSRF включён.</li>
 * </ol>
 * Без @EnableMethodSecurity аннотации @PreAuthorize молча игнорируются - защиты на методах не будет.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final SecurityExceptionHandler securityExceptionHandler;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, SecurityExceptionHandler securityExceptionHandler) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.securityExceptionHandler = securityExceptionHandler;
    }

    /**
     * Консоль H2 (только когда она включена: dev) живёт на отдельной цепочке: ей нужны открытый доступ
     * и разрешённые frame-ы (консоль - это страница с iframe).
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnProperty(name = "spring.h2.console.enabled", havingValue = "true")
    public SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/h2-console/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    /** REST API: JWT, stateless. */
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
                // эта цепочка обслуживает только адреса API; остальное достаётся веб-цепочке
                .securityMatcher("/api/**", "/tasks/**", "/auth/**",
                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                // CSRF защищает сессионные приложения с cookie; у REST API с токеном в заголовке он не нужен
                // (а если оставить включённым, все POST/PUT/DELETE получат 403)
                .csrf(csrf -> csrf.disable())
                // сервер не хранит состояние: каждый запрос аутентифицируется токеном
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // порядок важен: первое совпавшее правило выигрывает
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout", "/auth/logout").authenticated()
                        .requestMatchers("/api/auth/**", "/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                // JSON вместо HTML для 401 и 403
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Веб-интерфейс: форма логина, сессия в cookie, CSRF включён (Thymeleaf сам добавляет скрытое поле _csrf
     * в формы с th:action). Анонимный пользователь, открывший /web/tasks, перенаправляется на /login.
     */
    @Bean
    @Order(3)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/css/**", "/js/**", "/images/**", "/favicon.ico")
                        .permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/web/tasks"))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"));
        return http.build();
    }

    /** BCrypt: односторонний хеш с солью; пароль в открытом виде нигде не хранится. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** AuthenticationManager использует DaoAuthenticationProvider: UserDetailsService + PasswordEncoder. */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
