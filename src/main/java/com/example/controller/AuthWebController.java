package com.example.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Страница входа и главная. Саму проверку логина/пароля делает Spring Security (POST /login):
 * контроллер только показывает форму.
 */
@Controller
public class AuthWebController {

    /** GET /login -> templates/auth/login.html (?error - неверные данные, ?logout - вы вышли). */
    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/web/tasks";
    }
}
