package com.postgrespulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

@Entity
@Table(name = "pulse_sources", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class DbSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Column(name = "db_name", nullable = false, length = 100)
    private String dbName;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "password_enc", nullable = false, columnDefinition = "TEXT")
    private String passwordEncrypted;

    @Column(name = "schema_filter", length = 200)
    private String schemaFilter;

    @Column(length = 255)
    private String tags;

    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceStatus status = SourceStatus.OFFLINE;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "last_analyzed_at")
    private OffsetDateTime lastAnalyzedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordEncrypted() {
        return passwordEncrypted;
    }

    public void setPasswordEncrypted(String passwordEncrypted) {
        this.passwordEncrypted = passwordEncrypted;
    }

    public String getSchemaFilter() {
        return schemaFilter;
    }

    public void setSchemaFilter(String schemaFilter) {
        this.schemaFilter = schemaFilter;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public SourceStatus getStatus() {
        return status;
    }

    public void setStatus(SourceStatus status) {
        this.status = status;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public OffsetDateTime getLastAnalyzedAt() {
        return lastAnalyzedAt;
    }

    public void setLastAnalyzedAt(OffsetDateTime lastAnalyzedAt) {
        this.lastAnalyzedAt = lastAnalyzedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
