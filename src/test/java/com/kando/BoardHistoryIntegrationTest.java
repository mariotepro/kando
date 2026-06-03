package com.kando;

import com.kando.model.BoardColumn;
import com.kando.model.Label;
import com.kando.model.Task;
import com.kando.model.TaskColumnHistory;
import com.kando.repository.BoardColumnRepository;
import com.kando.repository.LabelRepository;
import com.kando.repository.TaskColumnHistoryRepository;
import com.kando.repository.TaskRepository;
import com.kando.service.BoardService;
import com.kando.service.SetupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:history;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=false"
})
class BoardHistoryIntegrationTest {

    @MockitoBean
    SetupService setupService;

    @Autowired BoardService boardService;
    @Autowired BoardColumnRepository columnRepository;
    @Autowired LabelRepository labelRepository;
    @Autowired TaskRepository taskRepository;
    @Autowired TaskColumnHistoryRepository historyRepository;

    private BoardColumn colHoy;
    private BoardColumn colHecho;
    private Label labelUrgente;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        taskRepository.deleteAll();
        labelRepository.deleteAll();
        columnRepository.deleteAll();

        colHoy = new BoardColumn();
        colHoy.setName("Hoy");
        colHoy.setPosition(0);
        colHoy = columnRepository.save(colHoy);

        colHecho = new BoardColumn();
        colHecho.setName("Hecho");
        colHecho.setDone(true);
        colHecho.setPosition(1);
        colHecho = columnRepository.save(colHecho);

        labelUrgente = new Label();
        labelUrgente.setName("urgente");
        labelUrgente.setColor("#ef4444");
        labelUrgente = labelRepository.save(labelUrgente);
    }

    @Test
    void createQuick_recordsInitialColumnHistory() {
        Task task = boardService.createQuick("Mi primera tarea", colHoy.getId(), labelUrgente.getId());

        List<TaskColumnHistory> history = historyRepository.findByTaskIdOrderByMovedAtAsc(task.getId());

        assertThat(history).singleElement().satisfies(entry -> {
            assertThat(entry.getTaskId()).isEqualTo(task.getId());
            assertThat(entry.getColumnId()).isEqualTo(colHoy.getId());
            assertThat(entry.getColumnName()).isEqualTo("Hoy");
            assertThat(entry.isColumnDone()).isFalse();
            assertThat(entry.getEventType()).isEqualTo(TaskColumnHistory.EVENT_CREATED);
        });
    }

    @Test
    void moveTask_recordsHistoryAndCompletionDate() {
        Task task = boardService.createQuick("Mover a hecho", colHoy.getId(), labelUrgente.getId());

        boardService.moveTask(task.getId(), colHecho.getId(), 0, null);

        List<TaskColumnHistory> history = historyRepository.findByTaskIdOrderByMovedAtAsc(task.getId());
        Instant completionDate = boardService.findCompletionDate(task.getId()).orElse(null);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getColumnName()).isEqualTo("Hoy");
        assertThat(history.get(0).isColumnDone()).isFalse();
        assertThat(history.get(0).getEventType()).isEqualTo(TaskColumnHistory.EVENT_CREATED);
        assertThat(history.get(1).getColumnName()).isEqualTo("Hecho");
        assertThat(history.get(1).isColumnDone()).isTrue();
        assertThat(history.get(1).getEventType()).isEqualTo(TaskColumnHistory.EVENT_COLUMN_CHANGE);
        assertThat(completionDate).isEqualTo(history.get(1).getMovedAt());
    }
}
