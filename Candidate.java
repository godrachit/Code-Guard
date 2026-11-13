package com.codeguard.model;
import jakarta.persistence.*;
import java.time.*;
import org.hibernate.annotations.DynamicUpdate;
@Entity @DynamicUpdate
public class Candidate {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  @Column(unique = true, nullable = false) public String candidateId;
  @Column(unique = true, nullable = false) public String sessionId;
  @ManyToOne(optional = false) public Exam exam;
  public String name, college, rollNo, regNo, email, mobile, course, branch, semester;
  public String ip, browser, os, deviceType, screenRes, language, timezone;
  @Column(length = 400) public String userAgent;
  public LocalDateTime registeredAt = LocalDateTime.now(), startedAt, submittedAt, lastSeen;
  public String status = "REGISTERED"; // REGISTERED | IN_PROGRESS | SUBMITTED
  public boolean screenSharing, recording;
  public int violationCount;

  public long remainingSeconds() {
    if (startedAt == null) return exam.durationMinutes * 60L;
    return Duration.between(LocalDateTime.now(), startedAt.plusMinutes(exam.durationMinutes)).getSeconds();
  }
  public boolean isOnline() {
    return "IN_PROGRESS".equals(status) && lastSeen != null && lastSeen.isAfter(LocalDateTime.now().minusSeconds(30));
  }
}
