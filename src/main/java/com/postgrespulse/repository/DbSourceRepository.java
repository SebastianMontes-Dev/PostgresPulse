package com.postgrespulse.repository;

import com.postgrespulse.domain.DbSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DbSourceRepository extends JpaRepository<DbSource, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<DbSource> findByNameIgnoreCase(String name);

    List<DbSource> findByEnabledTrueOrderByNameAsc();
}
