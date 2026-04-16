package com.recruiter.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "screening_batches")
public class ScreeningBatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, columnDefinition = "text")
    private String jobDescriptionText;

    @Column(nullable = false)
    private int shortlistCount;

    @Column(nullable = false, length = 30)
    private String scoringMode = "heuristic";

    @Column(length = 40)
    private String screeningPackage = "QUICK_SCREEN";

    @Column(length = 50)
    private String sector;

    @Column(nullable = false)
    private int totalCvsReceived;

    @Column(nullable = false)
    private int candidatesScored;

    @Column(columnDefinition = "text")
    private String aiJobDescriptionProfileJson;

    @Column(length = 100)
    private String promptVersions;

    @Column(precision = 5, scale = 1)
    private java.math.BigDecimal shortlistThreshold;

    private Integer aiPromptTokens;

    private Integer aiCompletionTokens;

    private Integer aiTotalTokens;

    @Column(precision = 8, scale = 4)
    private java.math.BigDecimal aiEstimatedCostUsd;

    private Long processingTimeMs;

    @OneToMany(mappedBy = "screeningBatch", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CandidateEvaluationEntity> candidateEvaluations = new ArrayList<>();

    @OneToMany(mappedBy = "screeningBatch", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EliminatedCandidateEntity> eliminatedCandidates = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void addCandidateEvaluation(CandidateEvaluationEntity candidateEvaluation) {
        candidateEvaluations.add(candidateEvaluation);
        candidateEvaluation.setScreeningBatch(this);
    }

    public void addEliminatedCandidate(EliminatedCandidateEntity eliminatedCandidate) {
        eliminatedCandidates.add(eliminatedCandidate);
        eliminatedCandidate.setScreeningBatch(this);
    }

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getJobDescriptionText() {
        return jobDescriptionText;
    }

    public void setJobDescriptionText(String jobDescriptionText) {
        this.jobDescriptionText = jobDescriptionText;
    }

    public int getShortlistCount() {
        return shortlistCount;
    }

    public void setShortlistCount(int shortlistCount) {
        this.shortlistCount = shortlistCount;
    }

    public String getScoringMode() {
        return scoringMode;
    }

    public void setScoringMode(String scoringMode) {
        this.scoringMode = scoringMode;
    }

    public String getSector() {
        return sector;
    }

    public String getScreeningPackage() {
        return screeningPackage;
    }

    public void setScreeningPackage(String screeningPackage) {
        this.screeningPackage = screeningPackage;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public int getTotalCvsReceived() {
        return totalCvsReceived;
    }

    public void setTotalCvsReceived(int totalCvsReceived) {
        this.totalCvsReceived = totalCvsReceived;
    }

    public int getCandidatesScored() {
        return candidatesScored;
    }

    public void setCandidatesScored(int candidatesScored) {
        this.candidatesScored = candidatesScored;
    }

    public String getAiJobDescriptionProfileJson() {
        return aiJobDescriptionProfileJson;
    }

    public void setAiJobDescriptionProfileJson(String aiJobDescriptionProfileJson) {
        this.aiJobDescriptionProfileJson = aiJobDescriptionProfileJson;
    }

    public String getPromptVersions() {
        return promptVersions;
    }

    public void setPromptVersions(String promptVersions) {
        this.promptVersions = promptVersions;
    }

    public java.math.BigDecimal getShortlistThreshold() {
        return shortlistThreshold;
    }

    public void setShortlistThreshold(java.math.BigDecimal shortlistThreshold) {
        this.shortlistThreshold = shortlistThreshold;
    }

    public Integer getAiPromptTokens() {
        return aiPromptTokens;
    }

    public void setAiPromptTokens(Integer aiPromptTokens) {
        this.aiPromptTokens = aiPromptTokens;
    }

    public Integer getAiCompletionTokens() {
        return aiCompletionTokens;
    }

    public void setAiCompletionTokens(Integer aiCompletionTokens) {
        this.aiCompletionTokens = aiCompletionTokens;
    }

    public Integer getAiTotalTokens() {
        return aiTotalTokens;
    }

    public void setAiTotalTokens(Integer aiTotalTokens) {
        this.aiTotalTokens = aiTotalTokens;
    }

    public java.math.BigDecimal getAiEstimatedCostUsd() {
        return aiEstimatedCostUsd;
    }

    public void setAiEstimatedCostUsd(java.math.BigDecimal aiEstimatedCostUsd) {
        this.aiEstimatedCostUsd = aiEstimatedCostUsd;
    }

    public Long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(Long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    public List<CandidateEvaluationEntity> getCandidateEvaluations() {
        return candidateEvaluations;
    }

    public List<EliminatedCandidateEntity> getEliminatedCandidates() {
        return eliminatedCandidates;
    }
}
