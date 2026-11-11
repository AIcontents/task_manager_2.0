package com.example.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Перехватывает каждый запрос: достаёт JWT из заголовка Authorization: Bearer ..., проверяет его и кладёт
 * аутентификацию в SecurityContext. Без токена запрос просто идёт дальше анонимным - решение
 * «пускать или нет» принимают правила SecurityConfig (anyRequest().authenticated() -> 401).
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response); // без токена - пропускаем дальше без аутентификации
            return;
        }

        String jwt = authHeader.substring(BEARER_PREFIX.length());
        try {
            String username = jwtService.extractUsername(jwt);
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (ExpiredJwtException e) {
            SecurityExceptionHandler.writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "Token expired");
            return; // запрос дальше не идёт: клиенту нужно получить новый токен (POST /auth/refresh)
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
            // подделанный / повреждённый токен или удалённый пользователь: остаёмся анонимными -> 401 от EntryPoint
            log.debug("JWT отклонён: {}", e.getMessage());
        }

        chain.doFilter(request, response); // ОБЯЗАТЕЛЬНО: иначе запрос зависнет и до контроллера не дойдёт
    }
}
