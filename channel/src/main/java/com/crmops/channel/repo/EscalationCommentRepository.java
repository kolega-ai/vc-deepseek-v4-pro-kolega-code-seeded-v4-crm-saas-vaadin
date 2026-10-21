package com.crmops.channel.repo;

import com.crmops.channel.domain.EscalationComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EscalationCommentRepository extends JpaRepository<EscalationComment, Long> {

    List<EscalationComment> findByEscalationIdOrderByCreatedAtAsc(Long escalationId);
}
