package com.codeguard.service;
import com.codeguard.model.*;
import com.codeguard.repo.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ExamService {
  private final SubmissionRepository submissions; private final CandidateRepository candidates;
  private final CodeRunner runner; private final AdminSocket socket;
  public ExamService(SubmissionRepository s, CandidateRepository c, CodeRunner r, AdminSocket a) { submissions = s; candidates = c; runner = r; socket = a; }

  public Submission upsert(Candidate c, Question q, String code) {
    Submission s = submissions.findByCandidateAndQuestion(c, q).orElseGet(() -> {
      Submission n = new Submission(); n.candidate = c; n.question = q; return n; });
    s.code = code; s.updatedAt = LocalDateTime.now();
    return submissions.save(s);
  }

  public static boolean same(String a, String b) { return norm(a).equals(norm(b)); }
  private static String norm(String s) {
    if (s == null) return "";
    return s.replace("\r", "").lines().map(String::stripTrailing).collect(Collectors.joining("\n")).strip();
  }

  /** Final grading: sample + hidden test must both match to earn the question's marks. Idempotent. */
  public void finish(Candidate c) {
    if ("SUBMITTED".equals(c.status)) return;
    for (Question q : c.exam.questions) {
      Submission s = submissions.findByCandidateAndQuestion(c, q).orElse(null);
      if (s == null) continue;
      if (s.code != null && !s.code.isBlank()) {
        String out = runner.run(s.code, q.sampleInput);
        boolean ok = same(out, q.sampleOutput);
        if (ok && q.hiddenInput != null && !q.hiddenInput.isBlank()) {
          out = runner.run(s.code, q.hiddenInput);
          ok = same(out, q.hiddenOutput);
        }
        s.lastOutput = out; s.passed = ok; s.marks = ok ? q.marks : 0;
      }
      submissions.save(s);
    }
    c.status = "SUBMITTED"; c.submittedAt = LocalDateTime.now(); c.screenSharing = false; c.recording = false;
    candidates.save(c);
    socket.broadcast(Map.of("kind", "status", "candidate", c.name, "type", "SUBMITTED", "severity", "INFO"));
  }
}
