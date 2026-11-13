package com.codeguard.repo;
import com.codeguard.model.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
public interface CandidateRepository extends JpaRepository<Candidate, Long> {
  Optional<Candidate> findBySessionId(String sessionId);
  boolean existsByExamAndEmailIgnoreCase(Exam exam, String email);
  boolean existsByExamAndRollNoIgnoreCase(Exam exam, String rollNo);
  List<Candidate> findByExamOrderByRegisteredAtDesc(Exam exam);
  List<Candidate> findAllByOrderByRegisteredAtDesc();
  long countByStatus(String status);
  long countByStatusAndLastSeenAfter(String status, LocalDateTime t);
  @Modifying @Transactional @Query("update Candidate c set c.lastSeen = :t where c.id = :id")
  void touch(@Param("id") Long id, @Param("t") LocalDateTime t);
  @Modifying @Transactional @Query("update Candidate c set c.violationCount = c.violationCount + 1 where c.id = :id")
  void bump(@Param("id") Long id);
  @Modifying @Transactional @Query("update Candidate c set c.screenSharing = :s, c.recording = :s where c.id = :id")
  void setSharing(@Param("id") Long id, @Param("s") boolean s);
}
