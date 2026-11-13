package com.codeguard.repo;
import com.codeguard.model.Exam;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ExamRepository extends JpaRepository<Exam, Long> { Optional<Exam> findByToken(String token); }
