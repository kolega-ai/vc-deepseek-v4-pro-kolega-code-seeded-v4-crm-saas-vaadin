package com.crmops.channel.repo;

import com.crmops.channel.domain.DocumentMeta;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentMetaRepository extends JpaRepository<DocumentMeta, Long> {

    @EntityGraph(attributePaths = "org")
    List<DocumentMeta> findByOrgIdOrderByUploadedAtDesc(Long orgId);
}
