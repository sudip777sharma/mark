package dev.mark.task.repository;

import dev.mark.task.entity.LlmInteractionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LlmInteractionLogRepository extends JpaRepository<LlmInteractionLogEntity, Long> {
    List<LlmInteractionLogEntity> findByTaskIdOrderByCreatedAtAsc(String taskId);
}
