package com.codeguard.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
public class Submission {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  @ManyToOne(optional = false) public Candidate candidate;
  @ManyToOne(optional = false) public Question question;
  @Column(columnDefinition = "TEXT") public String code, lastOutput;
  public boolean passed;
  public int marks;
  public LocalDateTime updatedAt = LocalDateTime.now();
}
