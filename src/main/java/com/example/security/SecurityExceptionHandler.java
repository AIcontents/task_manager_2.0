package com.example.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JSON вместо HTML-страницы для ошибок Spring Security:
 * 401 (нет или неверный токен) и 403 (токен есть, но прав не хватает).
 * <p>
 * Эти исключения возникают в цепочке фильтров ДО DispatcherServlet, поэтому @ControllerAdvice их не видит -
 * для них нужны именно AuthenticationEntryPoint и AccessDeniedHandler.
 */
@Component
public class SecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    /** 401: пользователь не аутентифицирован. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }

    /** 403: пользователь аутентифицирован, но у него недостаточно прав. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        writeJson(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden");
    }

    /** Пишет {"error":"...","status":NNN}. Используется и JwtAuthFilter (просроченный токен). */
    public static void writeJson(HttpServletResponse response, int status, String error) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + error + "\",\"status\":" + status + "}");
    }
}
