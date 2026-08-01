package com.postgrespulse.repository;

import com.postgrespulse.domain.CheckResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {

    List<CheckResult> findBySnapshotIdOrderByIdAsc(Long snapshotId);
}
