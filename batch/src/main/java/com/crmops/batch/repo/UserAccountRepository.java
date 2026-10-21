package com.crmops.batch.repo;

import com.crmops.batch.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    List<UserAccount> findByRoleAndEnabledTrue(String role);
}
