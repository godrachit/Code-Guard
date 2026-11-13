package com.codeguard.model;
import jakarta.persistence.*;
@Entity
public class Question {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  @ManyToOne(optional = false) public Exam exam;
  public String title, difficulty;
  public int marks;
  @Column(columnDefinition = "TEXT") public String statement, sampleInput, sampleOutput, hiddenInput, hiddenOutput;
}
