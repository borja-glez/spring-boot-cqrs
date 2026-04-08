package com.borjaglez.cqrs.example.outbox.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

  List<OutboxEventEntity> findTop20ByStatusOrderByOccurredAtAsc(OutboxStatus status);
}
