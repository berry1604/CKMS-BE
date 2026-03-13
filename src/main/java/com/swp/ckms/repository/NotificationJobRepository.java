package com.swp.ckms.repository;

import com.swp.ckms.entity.NotificationJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationJobRepository extends JpaRepository<NotificationJob, Long> {

    @Query("SELECT n FROM NotificationJob n WHERE n.status = 'PENDING' " +
           "OR (n.status = 'FAILED' AND (n.nextRetryAt IS NULL OR n.nextRetryAt <= :now)) " +
           "ORDER BY n.createdAt ASC")
    List<NotificationJob> findJobsToProcess(LocalDateTime now, Pageable pageable);

    boolean existsByDeduplicationKey(String deduplicationKey);
}
