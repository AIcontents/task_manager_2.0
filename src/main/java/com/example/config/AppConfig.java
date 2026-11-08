package com.example.config;

import com.example.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Java-конфигурация: бины, которые создаются через @Bean, а не через @Component.
 */
@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    /**
     * @Qualifier явно просит бин с именем priorityTaskRepository (PriorityTaskRepository),
     * даже если по умолчанию (@Primary) используется InMemoryTaskRepository.
     * <p>
     * PriorityTaskRepository активен только в профиле prod (или dev,prod одновременно),
     * поэтому зависимость необязательная: ObjectProvider.getIfAvailable() вернёт null, если бина нет.
     */
    @Bean
    public PriorityRepositoryInfo priorityRepositoryInfo(
            @Qualifier("priorityTaskRepository") ObjectProvider<TaskRepository> priorityRepository) {
        TaskRepository repository = priorityRepository.getIfAvailable();
        String name = repository != null
                ? repository.getClass().getSimpleName()
                : "PriorityTaskRepository (не активен в текущем профиле)";
        log.info("Через @Qualifier получен репозиторий: {}", name);
        return new PriorityRepositoryInfo(name);
    }

    /** Простой value-объект с именем репозитория, полученного через @Qualifier. */
    public record PriorityRepositoryInfo(String repositoryName) {
    }
}
