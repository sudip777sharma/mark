package dev.mark.trajectory.repository;

import dev.mark.trajectory.entity.ExecutionTrajectoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExecutionTrajectoryRepository extends JpaRepository<ExecutionTrajectoryEntity, UUID> {
    List<ExecutionTrajectoryEntity> findByIntentAndVerifiedTrue(String intent);
    List<ExecutionTrajectoryEntity> findByVerifiedTrueOrderByCreatedAtDesc();
    long countByIntentAndVerifiedTrue(String intent);
}
