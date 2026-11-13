package com.codeguard.service;
import com.codeguard.model.*;
import com.codeguard.repo.*;
import com.codeguard.util.Util;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ViolationService {
  /** Configurable rule table: event type -> severity. */
  public static final Map<String, String> SEVERITY = Map.of(
      "TAB_HIDDEN", "WARNING", "WINDOW_BLUR", "WARNING", "FULLSCREEN_EXIT", "WARNING",
      "COPY", "INFO", "CUT", "INFO", "PASTE", "WARNING",
      "SCREEN_SHARE_STOPPED", "CRITICAL", "RECORDING_ERROR", "CRITICAL", "NOT_ENTIRE_SCREEN", "HIGH");
  private static final int ESCALATE_AFTER = 4; // 5th WARNING onwards becomes HIGH_RISK
  private final ViolationRepository violations; private final CandidateRepository candidates; private final AdminSocket socket;
  public ViolationService(ViolationRepository v, CandidateRepository c, AdminSocket s) { violations = v; candidates = c; socket = s; }

  /** @return false if the event type is unknown. Events are signals for human review, not proof. */
  public boolean record(Candidate c, String type, String desc) {
    String sev = SEVERITY.get(type);
    if (sev == null) return false;
    if ("WARNING".equals(sev) && violations.countByCandidateAndSeverity(c, "WARNING") >= ESCALATE_AFTER) sev = "HIGH_RISK";
    Violation v = new Violation();
    v.candidate = c; v.type = type; v.severity = sev; v.description = Util.cut(desc, 300);
    violations.save(v);
    candidates.bump(c.id);
    socket.broadcast(Map.of("kind", "violation", "candidate", c.name, "type", type, "severity", sev));
    return true;
  }
}
