package com.kando;

import com.kando.model.BoardColumn;
import com.kando.model.KandoUser;
import com.kando.model.Label;
import com.kando.model.Task;
import jakarta.servlet.RequestDispatcher;
import com.kando.repository.KandoUserRepository;
import com.kando.repository.LabelRepository;
import com.kando.repository.BoardColumnRepository;
import com.kando.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;INIT=CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=false"
})
class KandoApplicationTests {

    @Autowired
    BoardColumnRepository boardColumnRepository;
    @Autowired
    LabelRepository labelRepository;
    @Autowired
    TaskRepository taskRepository;
    @Autowired
    KandoUserRepository userRepository;
    @Autowired
    MockMvc mockMvc;

    @Test
    void contextLoads() {
        assertThat(boardColumnRepository.findAllByOrderByPositionAsc())
            .extracting("name")
            .containsExactly("Hoy", "Planificado", "En espera", "Hecho");
    }

    @Test
    void boardPageRendersWithoutLazyLoadingErrors() throws Exception {
        KandoUser mario = new KandoUser();
        mario.setUsername("mario");
        mario.setPassword("hash");
        userRepository.save(mario);

        BoardColumn today = boardColumnRepository.findAllByOrderByPositionAsc().getFirst();

        Label urgent = new Label();
        urgent.setName("urgente");
        urgent.setColor("#ef4444");
        urgent = labelRepository.save(urgent);

        Task task = new Task();
        task.setTitle("Revisar bug visual");
        task.setColumn(today);
        task.setPosition(0);
        task.getLabels().add(urgent);
        taskRepository.save(task);

        Task subtask = new Task();
        subtask.setTitle("Corregir estilos");
        subtask.setColumn(today);
        subtask.setParentTask(task);
        subtask.setPosition(1);
        taskRepository.save(subtask);

        mockMvc.perform(get("/board").with(user("mario")))
            .andExpect(status().isOk())
            .andExpect(view().name("board"))
            .andExpect(content().string(containsString("Revisar bug visual")))
            .andExpect(content().string(containsString("urgente")))
            .andExpect(content().string(containsString("task-card-subtask")))
            .andExpect(content().string(containsString("Corregir estilos")));
    }

    @Test
    void errorPageRendersCustomTemplateForNotFound() throws Exception {
        mockMvc.perform(get("/error")
                .accept(MediaType.TEXT_HTML)
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/ruta-inexistente"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("error"))
            .andExpect(content().string(containsString("No hemos encontrado esa página")))
            .andExpect(content().string(containsString("/ruta-inexistente")));
    }
}
