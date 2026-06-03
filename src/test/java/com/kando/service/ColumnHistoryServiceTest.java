package com.kando.service;

import com.kando.model.BoardColumn;
import com.kando.model.Task;
import com.kando.model.TaskColumnHistory;
import com.kando.repository.TaskColumnHistoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ColumnHistoryServiceTest {

    @Mock
    TaskColumnHistoryRepository historyRepository;

    @Mock
    PlatformTransactionManager transactionManager;

    @InjectMocks
    ColumnHistoryService columnHistoryService;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void recordCreation_withoutTransaction_savesCreatedEvent() {
        // Data
        Task task = task(5L);
        BoardColumn column = column(1L, "Hoy", false);
        ArgumentCaptor<TaskColumnHistory> historyCaptor = ArgumentCaptor.forClass(TaskColumnHistory.class);

        // Mock methods
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Invoke method
        columnHistoryService.recordCreation(task, column);

        // Asserts
        verify(historyRepository).save(historyCaptor.capture());
        TaskColumnHistory savedEntry = historyCaptor.getValue();
        assertThat(savedEntry.getTaskId()).isEqualTo(5L);
        assertThat(savedEntry.getColumnId()).isEqualTo(1L);
        assertThat(savedEntry.getColumnName()).isEqualTo("Hoy");
        assertThat(savedEntry.isColumnDone()).isFalse();
        assertThat(savedEntry.getEventType()).isEqualTo(TaskColumnHistory.EVENT_CREATED);
    }

    @Test
    void recordColumnChange_withActiveTransaction_defersSaveUntilAfterCommit() {
        // Data
        Task task = task(8L);
        BoardColumn column = column(2L, "Hecho", true);
        ArgumentCaptor<TaskColumnHistory> historyCaptor = ArgumentCaptor.forClass(TaskColumnHistory.class);

        // Mock methods
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        try {
            // Invoke method
            columnHistoryService.recordColumnChange(task, column);

            // Asserts
            verify(historyRepository, never()).save(any());
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
            verify(historyRepository).save(historyCaptor.capture());
            assertThat(historyCaptor.getValue().getEventType()).isEqualTo(TaskColumnHistory.EVENT_COLUMN_CHANGE);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void recordColumnChange_whenRepositoryFails_doesNotPropagateException() {
        // Data
        Task task = task(9L);
        BoardColumn column = column(3L, "Planificado", false);

        // Mock methods
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(historyRepository.save(any())).thenThrow(new IllegalStateException("db error"));

        // Invoke method
        assertDoesNotThrow(() -> columnHistoryService.recordColumnChange(task, column));

        // Asserts
        verify(historyRepository).save(any());
    }

    @Test
    void recordCreation_missingIdentifiers_skipsHistoryWrite() {
        // Data
        Task task = task(null);
        BoardColumn column = column(1L, "Hoy", false);

        // Invoke method
        columnHistoryService.recordCreation(task, column);

        // Asserts
        verify(historyRepository, never()).save(any());
    }

    private Task task(Long id) {
        Task task = new Task();
        task.setId(id);
        return task;
    }

    private BoardColumn column(Long id, String name, boolean done) {
        BoardColumn column = new BoardColumn();
        column.setId(id);
        column.setName(name);
        column.setDone(done);
        return column;
    }
}
