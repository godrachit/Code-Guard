package com.codeguard.repo;
import com.codeguard.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ViolationRepository extends JpaRepository<Violation, Long> {
  List<Violation> findTop10ByOrderByOccurredAtDesc();
  List<Violation> findByCandidateOrderByOccurredAtDesc(Candidate c);
  long countByCandidateAndSeverity(Candidate c, String severity);
}
