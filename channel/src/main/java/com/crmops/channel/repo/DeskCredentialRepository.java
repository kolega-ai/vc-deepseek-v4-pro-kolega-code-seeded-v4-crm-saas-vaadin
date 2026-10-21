package com.crmops.channel.repo;

import com.crmops.channel.domain.DeskCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeskCredentialRepository extends JpaRepository<DeskCredential, Long> {

    Optional<DeskCredential> findByOperatorIgnoreCase(String operator);
}
