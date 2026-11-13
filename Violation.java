package com.codeguard.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
public class Violation {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  @ManyToOne(optional = false) public Candidate candidate;
  public String type, severity;
  @Column(length = 400) public String description;
  public LocalDateTime occurredAt = LocalDateTime.now();
}
