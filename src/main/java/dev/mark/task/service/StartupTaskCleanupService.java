package dev.mark.task.service;

import org.springframework.stereotype.Service;

/**
 * Legacy startup cleanup service, now refactored to delegate to StartupTaskRecoveryService
 * for durable checkpoint reconciliation.
 */
@Service
public class StartupTaskCleanupService {

    private final StartupTaskRecoveryService recoveryService;

    public StartupTaskCleanupService(StartupTaskRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    public void cleanupZombieTasks() {
        recoveryService.reconcileInterruptedTasks();
    }
}
