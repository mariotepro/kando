package com.kando.service;

import com.kando.model.BoardColumn;
import com.kando.model.Task;
import com.kando.model.TaskColumnHistory;
import com.kando.repository.TaskColumnHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Records task column transitions after the board transaction commits so that
 * the history write never interferes with the originating task change.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ColumnHistoryService {

    private final TaskColumnHistoryRepository historyRepository;
    private final PlatformTransactionManager transactionManager;

    /**
     * Persists the initial creation event of a task in the board history.
     *
     * @param task created task
     * @param column initial task column
     */
    public void recordCreation(Task task, BoardColumn column) {
        writeHistory(task, column, TaskColumnHistory.EVENT_CREATED);
    }

    /**
     * Persists a column-change event of a task in the board history.
     *
     * @param task moved or updated task
     * @param column destination task column
     */
    public void recordColumnChange(Task task, BoardColumn column) {
        writeHistory(task, column, TaskColumnHistory.EVENT_COLUMN_CHANGE);
    }

    private void writeHistory(Task task, BoardColumn column, String eventType) {
        Long taskId = task.getId();
        Long columnId = column.getId();
        String columnName = column.getName();
        boolean columnDone = column.isDone();

        if (taskId == null || columnId == null) {
            log.debug("Skipping history record because taskId={} or columnId={} is null", taskId, columnId);
            return;
        }

        Runnable writeHistory = () -> persistInNewTransaction(taskId, columnId, columnName, columnDone, eventType);
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    writeHistory.run();
                }
            });
            return;
        }

        writeHistory.run();
    }

    private void persistInNewTransaction(Long taskId,
                                         Long columnId,
                                         String columnName,
                                         boolean columnDone,
                                         String eventType) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> saveHistoryEntry(taskId, columnId, columnName, columnDone, eventType));
    }

    private void saveHistoryEntry(Long taskId,
                                  Long columnId,
                                  String columnName,
                                  boolean columnDone,
                                  String eventType) {
        TaskColumnHistory entry = new TaskColumnHistory();
        entry.setTaskId(taskId);
        entry.setColumnId(columnId);
        entry.setColumnName(columnName);
        entry.setColumnDone(columnDone);
        entry.setEventType(eventType);

        try {
            historyRepository.save(entry);
            log.debug("Recorded {} history event for task {} -> '{}'", eventType, taskId, columnName);
        } catch (Exception e) {
            log.warn("Could not persist {} history event for task {} -> '{}': {}",
                eventType, taskId, columnName, e.getMessage());
            log.debug("Column history write failure", e);
        }
    }
}
