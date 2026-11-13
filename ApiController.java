package com.codeguard.web;
import com.codeguard.model.*;
import com.codeguard.repo.*;
import com.codeguard.service.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Endpoints used by the candidate's browser. Authorised by the unguessable session UUID. */
@RestController @RequestMapping("/api/session/{sid}")
public class ApiController {
  private static final long MAX_CHUNK = 8_000_000;
  private final CandidateRepository candidates; private final QuestionRepository questions; private final ViolationService violations;
  private final ExamService service; private final CodeRunner runner; private final RecordingStore store; private final AdminSocket socket;
  public ApiController(CandidateRepository c, QuestionRepository q, ViolationService v, ExamService s, CodeRunner r, RecordingStore st, AdminSocket a) {
    candidates = c; questions = q; violations = v; service = s; runner = r; store = st; socket = a; }

  private Candidate cand(String sid) { return candidates.findBySessionId(sid).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }
  private Candidate live(String sid) {
    Candidate c = cand(sid);
    if (!"IN_PROGRESS".equals(c.status)) throw new ResponseStatusException(HttpStatus.CONFLICT);
    if (c.remainingSeconds() < -20) { service.finish(c); throw new ResponseStatusException(HttpStatus.FORBIDDEN); } // server-side time limit
    return c;
  }
  private Question question(Candidate c, Map<String, Object> b) {
    try {
      Question q = questions.findById(((Number) b.get("questionId")).longValue()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
      if (!q.exam.id.equals(c.exam.id)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
      return q;
    } catch (ClassCastException | NullPointerException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
  }
  private static String code(Map<String, Object> b) {
    String s = String.valueOf(b.getOrDefault("code", ""));
    if (s.length() > 20000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
    return s;
  }

  @PostMapping("/start") public Map<String, Object> start(@PathVariable String sid) {
    Candidate c = cand(sid);
    if ("SUBMITTED".equals(c.status)) throw new ResponseStatusException(HttpStatus.CONFLICT);
    if (c.startedAt == null) { c.startedAt = LocalDateTime.now(); c.status = "IN_PROGRESS"; } // idempotent: resume keeps original start
    c.lastSeen = LocalDateTime.now(); c.screenSharing = true; c.recording = true;
    candidates.save(c);
    socket.broadcast(Map.of("kind", "status", "candidate", c.name, "type", "STARTED", "severity", "INFO"));
    return Map.of("remainingSeconds", c.remainingSeconds());
  }

  @PostMapping("/event") public ResponseEntity<Void> event(@PathVariable String sid, @RequestBody Map<String, String> b) {
    Candidate c = cand(sid);
    if (!"IN_PROGRESS".equals(c.status)) return ResponseEntity.status(HttpStatus.CONFLICT).build();
    String type = b.getOrDefault("type", "");
    if ("HEARTBEAT".equals(type)) { candidates.touch(c.id, LocalDateTime.now()); return ResponseEntity.noContent().build(); }
    if (!violations.record(c, type, b.get("description"))) return ResponseEntity.badRequest().build();
    if ("SCREEN_SHARE_STOPPED".equals(type)) candidates.setSharing(c.id, false);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/chunk") public ResponseEntity<Void> chunk(@PathVariable String sid, @RequestParam long part, @RequestBody byte[] data) throws IOException {
    live(sid);
    if (part <= 0 || data.length == 0 || data.length > MAX_CHUNK) return ResponseEntity.badRequest().build();
    Files.write(store.file(sid, part), data, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/save") public ResponseEntity<Void> save(@PathVariable String sid, @RequestBody Map<String, Object> b) {
    Candidate c = live(sid);
    service.upsert(c, question(c, b), code(b));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/run") public Map<String, Object> run(@PathVariable String sid, @RequestBody Map<String, Object> b) {
    Candidate c = live(sid);
    Question q = question(c, b); String code = code(b);
    service.upsert(c, q, code);
    String out = runner.run(code, q.sampleInput);
    return Map.of("output", out, "sampleMatch", ExamService.same(out, q.sampleOutput));
  }

  @PostMapping("/submit") public Map<String, Object> submit(@PathVariable String sid) {
    service.finish(cand(sid));
    return Map.of("ok", true);
  }
}
