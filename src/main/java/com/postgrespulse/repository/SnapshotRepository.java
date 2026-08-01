package com.postgrespulse.repository;

import com.postgrespulse.domain.Snapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {

    Page<Snapshot> findBySourceIdOrderByAnalyzedAtDesc(Long sourceId, Pageable pageable);

    List<Snapshot> findTop7BySourceIdOrderByAnalyzedAtDesc(Long sourceId);
}
