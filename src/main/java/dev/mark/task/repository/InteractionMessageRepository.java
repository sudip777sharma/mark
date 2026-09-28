package dev.mark.task.repository;

import dev.mark.task.entity.InteractionMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InteractionMessageRepository extends JpaRepository<InteractionMessageEntity, UUID> {
}
