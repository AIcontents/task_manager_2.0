package com.example.controller;

import com.example.dto.TaskRequest;
import com.example.dto.TaskResponse;
import com.example.exception.TaskLimitExceededException;
import com.example.exception.TaskNotFoundException;
import com.example.model.Priority;
import com.example.model.Status;
import com.example.model.User;
import com.example.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Веб-интерфейс задач. @Controller (а не @RestController) возвращает ИМЯ ШАБЛОНА, а не JSON:
 * "tasks/list" -> templates/tasks/list.html. Данные в шаблон передаются через Model (th:each, th:text).
 * TaskService не менялся: тот же сервис, что и у REST API.
 */
@Controller
@RequestMapping("/web/tasks")
public class TaskWebController {

    private static final String FORM_VIEW = "tasks/form";
    private static final String REDIRECT_TO_LIST = "redirect:/web/tasks";

    private final TaskService taskService;

    public TaskWebController(TaskService taskService) {
        this.taskService = taskService;
    }

    /** Значения для выпадающих списков формы: добавляются в модель КАЖДОГО запроса этого контроллера. */
    @ModelAttribute("priorities")
    public Priority[] priorities() {
        return Priority.values();
    }

    @ModelAttribute("statuses")
    public Status[] statuses() {
        return Status.values();
    }

    @GetMapping
    public String list(Model model, @AuthenticationPrincipal User currentUser) {
        model.addAttribute("tasks", taskService.getAll(currentUser));
        return "tasks/list";
    }

    /** Литеральный /new объявлен рядом с шаблоном /{id}: литеральный маршрут имеет приоритет. */
    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("taskRequest", new TaskRequest());
        return FORM_VIEW;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, @AuthenticationPrincipal User currentUser) {
        model.addAttribute("task", taskService.getOrThrow(id, currentUser));
        return "tasks/detail";
    }

    /**
     * @Valid проверяет TaskRequest по аннотациям (@NotBlank ...), результат попадает в BindingResult.
     * Ошибки есть - возвращаем ту же форму (th:errors покажет сообщения). Успех - redirect (Post-Redirect-Get):
     * иначе F5 повторно отправил бы POST и создал дубликат.
     */
    @PostMapping
    public String create(@Valid @ModelAttribute("taskRequest") TaskRequest request,
                         BindingResult result,
                         @AuthenticationPrincipal User currentUser,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return FORM_VIEW;
        }
        try {
            taskService.create(request, currentUser);
        } catch (TaskLimitExceededException e) {
            result.reject("task.limit", e.getMessage());
            return FORM_VIEW;
        }
        redirectAttributes.addFlashAttribute("message", "Задача создана");
        return REDIRECT_TO_LIST;
    }

    /** Форма редактирования - тот же шаблон form.html, но с заполненным объектом и taskId для action. */
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model, @AuthenticationPrincipal User currentUser) {
        TaskResponse task = taskService.getOrThrow(id, currentUser);
        TaskRequest form = new TaskRequest(task.getTitle(), task.getDescription(),
                task.getPriority() != null ? Priority.valueOf(task.getPriority()) : null);
        form.setStatus(task.getStatus() != null ? Status.valueOf(task.getStatus()) : null);
        model.addAttribute("taskRequest", form);
        model.addAttribute("taskId", id);
        return FORM_VIEW;
    }

    /** HTML-формы умеют только GET и POST, поэтому обновление - POST на /web/tasks/{id}. */
    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("taskRequest") TaskRequest request,
                         BindingResult result,
                         Model model,
                         @AuthenticationPrincipal User currentUser,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("taskId", id);
            return FORM_VIEW;
        }
        taskService.update(id, request, currentUser).orElseThrow(() -> new TaskNotFoundException(id));
        redirectAttributes.addFlashAttribute("message", "Задача обновлена");
        return REDIRECT_TO_LIST;
    }

    /** DELETE в HTML-форме недоступен - отдельный URL /delete. Удалять в интерфейсе может только ADMIN. */
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal User currentUser,
                         RedirectAttributes redirectAttributes) {
        taskService.delete(id, currentUser);
        redirectAttributes.addFlashAttribute("message", "Задача удалена");
        return REDIRECT_TO_LIST;
    }
}
