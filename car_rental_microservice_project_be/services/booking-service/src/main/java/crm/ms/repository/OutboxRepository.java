package crm.ms.repository;

import crm.ms.entity.Outbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {

    List<Outbox> findTop50ByIsPublishedFalseOrderByCreatedAtAsc();
}

