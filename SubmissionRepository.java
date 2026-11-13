package com.codeguard.repo;
import com.codeguard.model.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
  Optional<Submission> findByCandidateAndQuestion(Candidate c, Question q);
  List<Submission> findByCandidate(Candidate c);
}
