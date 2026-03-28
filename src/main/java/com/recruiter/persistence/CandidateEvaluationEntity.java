package com.recruiter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "candidate_evaluations")
public class CandidateEvaluationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screening_batch_id", nullable = false)
    private ScreeningBatchEntity screeningBatch;

    @Column(nullable = false, length = 255)
    private String candidateFilename;

    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal score;

    @Column(precision = 5, scale = 1)
    private BigDecimal requiredSkillsScore;

    @Column(precision = 5, scale = 1)
    private BigDecimal preferredSkillsScore;

    @Column(precision = 5, scale = 1)
    private BigDecimal experienceScore;

    @Column(precision = 5, scale = 1)
    private BigDecimal domainRelevanceScore;

    @Column(precision = 5, scale = 1)
    private BigDecimal qualificationsScore;

    @Column(precision = 5, scale = 1)
    private BigDecimal softSkillsScore;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(nullable = false)
    private int rankPosition;

    @Column(nullable = false)
    private boolean shortlisted;

    public Long getId() {
        return id;
    }

    public ScreeningBatchEntity getScreeningBatch() {
        return screeningBatch;
    }

    public void setScreeningBatch(ScreeningBatchEntity screeningBatch) {
        this.screeningBatch = screeningBatch;
    }

    public String getCandidateFilename() {
        return candidateFilename;
    }

    public void setCandidateFilename(String candidateFilename) {
        this.candidateFilename = candidateFilename;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public BigDecimal getRequiredSkillsScore() {
        return requiredSkillsScore;
    }

    public void setRequiredSkillsScore(BigDecimal requiredSkillsScore) {
        this.requiredSkillsScore = requiredSkillsScore;
    }

    public BigDecimal getPreferredSkillsScore() {
        return preferredSkillsScore;
    }

    public void setPreferredSkillsScore(BigDecimal preferredSkillsScore) {
        this.preferredSkillsScore = preferredSkillsScore;
    }

    public BigDecimal getExperienceScore() {
        return experienceScore;
    }

    public void setExperienceScore(BigDecimal experienceScore) {
        this.experienceScore = experienceScore;
    }

    public BigDecimal getDomainRelevanceScore() {
        return domainRelevanceScore;
    }

    public void setDomainRelevanceScore(BigDecimal domainRelevanceScore) {
        this.domainRelevanceScore = domainRelevanceScore;
    }

    public BigDecimal getQualificationsScore() {
        return qualificationsScore;
    }

    public void setQualificationsScore(BigDecimal qualificationsScore) {
        this.qualificationsScore = qualificationsScore;
    }

    public BigDecimal getSoftSkillsScore() {
        return softSkillsScore;
    }

    public void setSoftSkillsScore(BigDecimal softSkillsScore) {
        this.softSkillsScore = softSkillsScore;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public int getRankPosition() {
        return rankPosition;
    }

    public void setRankPosition(int rankPosition) {
        this.rankPosition = rankPosition;
    }

    public boolean isShortlisted() {
        return shortlisted;
    }

    public void setShortlisted(boolean shortlisted) {
        this.shortlisted = shortlisted;
    }
}
