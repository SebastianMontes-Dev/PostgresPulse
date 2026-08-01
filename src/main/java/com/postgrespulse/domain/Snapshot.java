package com.postgrespulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "pulse_snapshots")
public class Snapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private DbSource source;

    @Column(name = "health_score", precision = 5, scale = 2)
    private BigDecimal healthScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "analyzed_at", nullable = false)
    private OffsetDateTime analyzedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false, length = 20)
    private TriggerType triggeredBy = TriggerType.MANUAL;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_json")
    private Map<String, Object> rawJson;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DbSource getSource() {
        return source;
    }

    public void setSource(DbSource source) {
        this.source = source;
    }

    public BigDecimal getHealthScore() {
        return healthScore;
    }

    public void setHealthScore(BigDecimal healthScore) {
        this.healthScore = healthScore;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public void setStatus(AnalysisStatus status) {
        this.status = status;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public OffsetDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(OffsetDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }

    public TriggerType getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(TriggerType triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public Map<String, Object> getRawJson() {
        return rawJson;
    }

    public void setRawJson(Map<String, Object> rawJson) {
        this.rawJson = rawJson;
    }
}
