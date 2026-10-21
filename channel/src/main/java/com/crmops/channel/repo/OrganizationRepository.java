package com.crmops.channel.repo;

import com.crmops.channel.domain.Organization;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    @EntityGraph(attributePaths = "plan")
    List<Organization> findAll();

    @EntityGraph(attributePaths = "plan")
    List<Organization> findByIdInOrderByNameAsc(List<Long> ids);

    @EntityGraph(attributePaths = "plan")
    List<Organization> findByOwnerUserIdOrderByNameAsc(Long ownerUserId);

    @Query("select o from Organization o join fetch o.plan where o.renewalDate between :from and :to order by o.renewalDate asc")
    List<Organization> findRenewalsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    long countByStatus(String status);
}
