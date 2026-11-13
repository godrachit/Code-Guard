package com.codeguard.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;
@Entity
public class Exam {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  public String title;
  @Column(columnDefinition = "TEXT") public String description;
  public int durationMinutes = 60;
  @Column(unique = true, nullable = false) public String token;
  public boolean linkActive = true;
  public LocalDateTime expiresAt;
  public LocalDateTime createdAt = LocalDateTime.now();
  @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("id")
  public List<Question> questions = new ArrayList<>();
}
