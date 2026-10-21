package com.crmops.coremock.repo;

import com.crmops.coremock.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByOrgIdOrderByIssuedAtDesc(Long orgId);
}
