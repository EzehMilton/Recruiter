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

    @Column(length = 255)
    private String candidateName;

    @Column(nullable = false, length = 255)
    private String candidateFilename;

    @Column(columnDefinition = "text")
    private String extractedSkills;

    @Column
    private Integer yearsOfExperience;

    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal score;

    @Column(precision = 5, scale = 1)
    private BigDecimal skillScore;

    @Column(precision = 5, scale = 1)
    private BigDecimal keywordScore;

    @Column(precision = 5, scale = 1)
    private BigDecimal experienceScore;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Column(nullable = false, length = 30)
    private String scoringPath = "heuristic";

    @Column(columnDefinition = "text")
    private String aiCandidateProfileJson;

    @Column(columnDefinition = "text")
    private String aiFitAssessmentJson;

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

    public String getCandidateName() {
        return candidateName;
    }

    public void setCandidateName(String candidateName) {
        this.candidateName = candidateName;
    }

    public void setCandidateFilename(String candidateFilename) {
        this.candidateFilename = candidateFilename;
    }

    public String getExtractedSkills() {
        return extractedSkills;
    }

    public void setExtractedSkills(String extractedSkills) {
        this.extractedSkills = extractedSkills;
    }

    public Integer getYearsOfExperience() {
        return yearsOfExperience;
    }

    public void setYearsOfExperience(Integer yearsOfExperience) {
        this.yearsOfExperience = yearsOfExperience;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public BigDecimal getSkillScore() {
        return skillScore;
    }

    public void setSkillScore(BigDecimal skillScore) {
        this.skillScore = skillScore;
    }

    public BigDecimal getKeywordScore() {
        return keywordScore;
    }

    public void setKeywordScore(BigDecimal keywordScore) {
        this.keywordScore = keywordScore;
    }

    public BigDecimal getExperienceScore() {
        return experienceScore;
    }

    public void setExperienceScore(BigDecimal experienceScore) {
        this.experienceScore = experienceScore;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getScoringPath() {
        return scoringPath;
    }

    public void setScoringPath(String scoringPath) {
        this.scoringPath = scoringPath;
    }

    public String getAiCandidateProfileJson() {
        return aiCandidateProfileJson;
    }

    public void setAiCandidateProfileJson(String aiCandidateProfileJson) {
        this.aiCandidateProfileJson = aiCandidateProfileJson;
    }

    public String getAiFitAssessmentJson() {
        return aiFitAssessmentJson;
    }

    public void setAiFitAssessmentJson(String aiFitAssessmentJson) {
        this.aiFitAssessmentJson = aiFitAssessmentJson;
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
