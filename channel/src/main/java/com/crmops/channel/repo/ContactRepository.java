package com.crmops.channel.repo;

import com.crmops.channel.domain.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    List<Contact> findByOrgIdOrderByPrimaryDescLastNameAsc(Long orgId);

    Optional<Contact> findByOrgIdAndEmailIgnoreCase(Long orgId, String email);

    List<Contact> findByUserId(Long userId);
}
