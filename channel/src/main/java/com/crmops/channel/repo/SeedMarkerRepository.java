package com.crmops.channel.repo;

import com.crmops.channel.domain.SeedMarker;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeedMarkerRepository extends JpaRepository<SeedMarker, String> {
}
