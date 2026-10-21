package com.crmops.channel.service;

import com.crmops.channel.domain.Organization;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Filtered organization search. This is one of the application's canonical
 * precision-baseline locations:
 * <ul>
 *   <li>the sort column is bounded by an explicit whitelist before it is
 *       placed into the ORDER BY clause, and</li>
 *   <li>every filter VALUE is passed as a bound query parameter — user input
 *       is never concatenated into the JPQL text.</li>
 * </ul>
 */
@Service
public class OrganizationQueryService {

    private static final Set<String> SORT_COLUMNS = Set.of(
            "name", "createdAt", "updatedAt", "renewalDate", "healthScore");
    private static final Set<String> SORT_DIRECTIONS = Set.of("asc", "desc");

    @PersistenceContext
    private EntityManager entityManager;

    public record SearchCriteria(List<Long> orgIds, String search, String planCode, String status,
                                 String healthBucket, String sort, String direction) {
    }

    @Transactional(readOnly = true)
    public List<Organization> search(SearchCriteria criteria) {
        String sortColumn = whitelistSort(criteria.sort());
        String direction = criteria.direction() != null && SORT_DIRECTIONS.contains(criteria.direction().toLowerCase())
                ? criteria.direction().toLowerCase() : "asc";

        StringBuilder jpql = new StringBuilder(
                "select o from Organization o join fetch o.plan p where 1 = 1");
        List<String> clauses = new ArrayList<>();
        if (criteria.orgIds() != null && !criteria.orgIds().isEmpty()) {
            clauses.add("o.id in :orgIds");
        }
        if (criteria.search() != null && !criteria.search().isBlank()) {
            clauses.add("(lower(o.name) like :search or lower(o.industry) like :search or lower(o.country) like :search)");
        }
        if (criteria.planCode() != null && !criteria.planCode().isBlank()) {
            clauses.add("p.code = :planCode");
        }
        if (criteria.status() != null && !criteria.status().isBlank()) {
            // The status filter is validated against the enum before use so an
            // arbitrary string can never reach the query text.
            com.crmops.channel.domain.OrgStatus.valueOf(criteria.status());
            clauses.add("o.status = :status");
        }
        if (criteria.healthBucket() != null && !criteria.healthBucket().isBlank()) {
            clauses.add(healthClause(criteria.healthBucket()));
        }
        for (String clause : clauses) {
            jpql.append(" and ").append(clause);
        }
        jpql.append(" order by o.").append(sortColumn).append(' ').append(direction);

        TypedQuery<Organization> query = entityManager.createQuery(jpql.toString(), Organization.class);
        query.setMaxResults(200);
        if (criteria.orgIds() != null && !criteria.orgIds().isEmpty()) {
            query.setParameter("orgIds", criteria.orgIds());
        }
        if (criteria.search() != null && !criteria.search().isBlank()) {
            query.setParameter("search", "%" + criteria.search().trim().toLowerCase() + "%");
        }
        if (criteria.planCode() != null && !criteria.planCode().isBlank()) {
            query.setParameter("planCode", criteria.planCode());
        }
        if (criteria.status() != null && !criteria.status().isBlank()) {
            query.setParameter("status", com.crmops.channel.domain.OrgStatus.valueOf(criteria.status()));
        }
        return query.getResultList();
    }

    private static String healthClause(String bucket) {
        return switch (bucket) {
            case "EXCELLENT" -> "o.healthScore >= 80";
            case "GOOD" -> "o.healthScore >= 65 and o.healthScore < 80";
            case "AT_RISK" -> "o.healthScore >= 45 and o.healthScore < 65";
            case "CRITICAL" -> "o.healthScore < 45";
            default -> "1 = 0";
        };
    }

    private static String whitelistSort(String requested) {
        String column = requested == null ? "name" : requested;
        if (!SORT_COLUMNS.contains(column)) {
            // Unknown sort columns fall back to a stable default instead of
            // reaching the query text.
            return "name";
        }
        return column;
    }
}
