package com.crmops.batch.repo;

import com.crmops.batch.domain.EmailOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {

    List<EmailOutbox> findTop20ByStatusOrderByCreatedAtAsc(String status);
}
