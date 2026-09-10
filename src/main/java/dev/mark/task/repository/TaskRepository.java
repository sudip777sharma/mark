package dev.mark.task.repository;

import dev.mark.task.entity.TaskEntity;
import dev.mark.agent.model.AgentStatusModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

    /**
     * **Purpose**: Manages persistence and database operations for TaskEntity.
     * **Utility**: Reduces boilerplate code by providing built-in CRUD operations via Spring Data JPA.
     * **Application Flow**: Serves as the data access layer bridging business services and the database.
     * **Components**: Inherits standard JPA methods and defines findByStatusIn to filter tasks by multiple agent statuses.
     * **Integration Logic**: Executes queries triggered by services to enable status-based task retrieval for agent workflows.
     */

/**
 * - What it does: Acts as a Spring Data JPA repository for managing TaskEntity persistence and database operations.
 * - Why it is useful: Eliminates boilerplate database code by providing built-in CRUD operations and custom query capabilities using Spring Data.
 * - How it fits in the flow: Bridges the service layer and the database, allowing services to persist, update, and retrieve task data.
 * - Methods and variables: Inherits standard JPA methods and defines findByStatusIn(List<AgentStatusModel>) to filter tasks by multiple agent statuses.
 * - Logic and integration: Integrates into the application logic by executing database queries triggered by business services, enabling status-based task retrieval for agent workflows.
 */

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {
    List<TaskEntity> findByStatusIn(List<AgentStatusModel> statuses);
}
