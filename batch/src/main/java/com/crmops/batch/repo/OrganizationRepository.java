package com.crmops.batch.repo;

import com.crmops.batch.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    List<Organization> findByRenewalDateBetweenAndStatus(LocalDate from, LocalDate to, String status);
}
