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
@Table(name = "eliminated_candidates")
public class EliminatedCandidateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screening_batch_id", nullable = false)
    private ScreeningBatchEntity screeningBatch;

    @Column(nullable = false, length = 255)
    private String candidateName;

    @Column(nullable = false, length = 255)
    private String candidateFilename;

    @Column(nullable = false, precision = 5, scale = 1)
    private BigDecimal preFilterScore;

    @Column(columnDefinition = "text")
    private String matchedSkills;

    @Column(length = 50)
    private String scoreLabel;

    @Column(length = 255)
    private String eliminationReason;

    public Long getId() {
        return id;
    }

    public ScreeningBatchEntity getScreeningBatch() {
        return screeningBatch;
    }

    public void setScreeningBatch(ScreeningBatchEntity screeningBatch) {
        this.screeningBatch = screeningBatch;
    }

    public String getCandidateName() {
        return candidateName;
    }

    public void setCandidateName(String candidateName) {
        this.candidateName = candidateName;
    }

    public String getCandidateFilename() {
        return candidateFilename;
    }

    public void setCandidateFilename(String candidateFilename) {
        this.candidateFilename = candidateFilename;
    }

    public BigDecimal getPreFilterScore() {
        return preFilterScore;
    }

    public void setPreFilterScore(BigDecimal preFilterScore) {
        this.preFilterScore = preFilterScore;
    }

    public String getMatchedSkills() {
        return matchedSkills;
    }

    public void setMatchedSkills(String matchedSkills) {
        this.matchedSkills = matchedSkills;
    }

    public String getScoreLabel() {
        return scoreLabel;
    }

    public void setScoreLabel(String scoreLabel) {
        this.scoreLabel = scoreLabel;
    }

    public String getEliminationReason() {
        return eliminationReason;
    }

    public void setEliminationReason(String eliminationReason) {
        this.eliminationReason = eliminationReason;
    }
}
