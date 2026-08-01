package com.postgrespulse.domain;

import com.postgrespulse.repository.CheckResultRepository;
import com.postgrespulse.repository.DbSourceRepository;
import com.postgrespulse.repository.SnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DomainPersistenceTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pulse_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    DbSourceRepository sourceRepository;

    @Autowired
    SnapshotRepository snapshotRepository;

    @Autowired
    CheckResultRepository checkResultRepository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void flywayMigratesSchemaAndPersistsFullGraph() {
        DbSource saved = saveSampleSource();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(SourceStatus.OFFLINE);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(sourceRepository.existsByNameIgnoreCase("ventas demo")).isTrue();

        Snapshot savedSnapshot = saveSampleSnapshot(saved);

        Snapshot found = snapshotRepository.findById(savedSnapshot.getId()).orElseThrow();
        List<CheckResult> checks = checkResultRepository.findBySnapshotIdOrderByIdAsc(found.getId());

        assertThat(checks).hasSize(1);
        assertThat(checks.get(0).getCheckCode()).isEqualTo("SEQ_SCAN");
        assertThat(checks.get(0).getDetails()).containsKey("tables");

        assertThat(snapshotRepository.findBySourceIdOrderByAnalyzedAtDesc(saved.getId(), PageRequest.of(0, 10)))
                .hasSize(1);
    }

    @Test
    void deletingSourceCascadesSnapshotsAtDatabaseLevel() {
        DbSource saved = saveSampleSource();
        Snapshot savedSnapshot = saveSampleSnapshot(saved);

        entityManager.flush();
        entityManager.clear();
        sourceRepository.deleteById(saved.getId());
        entityManager.flush();

        Number remaining = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT count(*) FROM pulse_snapshots WHERE id = :id")
                .setParameter("id", savedSnapshot.getId())
                .getSingleResult();

        assertThat(remaining.intValue()).isZero();
    }

    private DbSource saveSampleSource() {
        DbSource source = new DbSource();
        source.setName("Ventas Demo");
        source.setHost("localhost");
        source.setPort(5433);
        source.setDbName("ventas_db");
        source.setUsername("demo");
        source.setPasswordEncrypted("cifrado-aes");
        source.setSchemaFilter("public");
        source.setTags("demo,core");
        return sourceRepository.save(source);
    }

    private Snapshot saveSampleSnapshot(DbSource source) {
        Snapshot snapshot = new Snapshot();
        snapshot.setSource(source);
        snapshot.setHealthScore(new BigDecimal("47.30"));
        snapshot.setStatus(AnalysisStatus.CRITICAL);
        snapshot.setAnalyzedAt(OffsetDateTime.now());
        snapshot.setTriggeredBy(TriggerType.MANUAL);
        snapshot.setRawJson(Map.of("version", "16.3"));
        Snapshot savedSnapshot = snapshotRepository.save(snapshot);

        CheckResult check = new CheckResult();
        check.setSnapshot(savedSnapshot);
        check.setCheckCode("SEQ_SCAN");
        check.setCategory(CheckCategory.PERFORMANCE);
        check.setStatus(AnalysisStatus.CRITICAL);
        check.setScore(new BigDecimal("35.00"));
        check.setMessage("3 tablas con 100% de scans secuenciales");
        check.setRecommendation("CREATE INDEX idx_ventas_cliente_id ON ventas(cliente_id);");
        check.setDetails(Map.of("tables", List.of(Map.of("table", "ventas", "seqScans", 15432))));
        checkResultRepository.save(check);
        return savedSnapshot;
    }
}
