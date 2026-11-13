package com.codeguard.web;
import com.codeguard.model.*;
import com.codeguard.repo.*;
import com.codeguard.service.RecordingStore;
import com.codeguard.util.Util;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class AdminController {
  private final ExamRepository exams; private final QuestionRepository questions; private final CandidateRepository candidates;
  private final ViolationRepository violations; private final SubmissionRepository submissions; private final RecordingStore store;
  @Value("${codeguard.base-url}") private String baseUrl;
  public AdminController(ExamRepository e, QuestionRepository q, CandidateRepository c, ViolationRepository v, SubmissionRepository s, RecordingStore r) {
    exams = e; questions = q; candidates = c; violations = v; submissions = s; store = r; }
  private static <T> T or404(Optional<T> o) { return o.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }

  @GetMapping("/") String root() { return "redirect:/admin"; }
  @GetMapping("/admin/login") String login() { return "admin/login"; }

  @GetMapping("/admin") String dashboard(Model m) {
    m.addAttribute("examCount", exams.count());
    m.addAttribute("candidateCount", candidates.count());
    m.addAttribute("active", candidates.countByStatus("IN_PROGRESS"));
    m.addAttribute("completed", candidates.countByStatus("SUBMITTED"));
    m.addAttribute("online", candidates.countByStatusAndLastSeenAfter("IN_PROGRESS", LocalDateTime.now().minusSeconds(30)));
    m.addAttribute("violationCount", violations.count());
    m.addAttribute("recent", violations.findTop10ByOrderByOccurredAtDesc());
    m.addAttribute("exams", exams.findAll());
    return "admin/dashboard";
  }

  @GetMapping("/admin/exams/new") String newExam() { return "admin/exam-form"; }

  @PostMapping("/admin/exams") String create(@RequestParam String title, @RequestParam(defaultValue = "") String description,
      @RequestParam int durationMinutes,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expiresAt) {
    Exam e = new Exam();
    e.title = Util.cut(title.trim(), 200); e.description = Util.cut(description, 4000);
    e.durationMinutes = Math.max(5, Math.min(durationMinutes, 300));
    e.expiresAt = expiresAt; e.token = Util.token(16);
    exams.save(e);
    return "redirect:/admin/exams/" + e.id;
  }

  @GetMapping("/admin/exams/{id}") String exam(@PathVariable Long id, Model m) {
    Exam e = or404(exams.findById(id));
    m.addAttribute("exam", e); m.addAttribute("link", baseUrl + "/test/" + e.token);
    m.addAttribute("candidates", candidates.findByExamOrderByRegisteredAtDesc(e));
    return "admin/exam";
  }

  @PostMapping("/admin/exams/{id}/questions") String addQuestion(@PathVariable Long id, @RequestParam String title,
      @RequestParam String statement, @RequestParam String difficulty, @RequestParam int marks,
      @RequestParam(defaultValue = "") String sampleInput, @RequestParam(defaultValue = "") String sampleOutput,
      @RequestParam(defaultValue = "") String hiddenInput, @RequestParam(defaultValue = "") String hiddenOutput) {
    Question q = new Question();
    q.exam = or404(exams.findById(id));
    q.title = Util.cut(title.trim(), 200); q.statement = statement; q.difficulty = Util.cut(difficulty, 20);
    q.marks = Math.max(0, Math.min(marks, 1000));
    q.sampleInput = sampleInput; q.sampleOutput = sampleOutput; q.hiddenInput = hiddenInput; q.hiddenOutput = hiddenOutput;
    questions.save(q);
    return "redirect:/admin/exams/" + id;
  }

  @PostMapping("/admin/exams/{id}/toggle") String toggle(@PathVariable Long id) {
    Exam e = or404(exams.findById(id)); e.linkActive = !e.linkActive; exams.save(e);
    return "redirect:/admin/exams/" + id;
  }

  @PostMapping("/admin/exams/{id}/delete") String delete(@PathVariable Long id) {
    Exam e = or404(exams.findById(id));
    if (candidates.findByExamOrderByRegisteredAtDesc(e).isEmpty()) exams.delete(e);
    return "redirect:/admin";
  }

  @GetMapping("/admin/monitor") String monitor(@RequestParam(defaultValue = "all") String filter, Model m) {
    List<Candidate> list = candidates.findAllByOrderByRegisteredAtDesc().stream().filter(c -> switch (filter) {
      case "active" -> "IN_PROGRESS".equals(c.status);
      case "completed" -> "SUBMITTED".equals(c.status);
      case "violations" -> c.violationCount > 0;
      case "disconnected" -> "IN_PROGRESS".equals(c.status) && !c.isOnline();
      default -> true; }).toList();
    m.addAttribute("list", list); m.addAttribute("filter", filter);
    return "admin/monitor";
  }

  @GetMapping("/admin/candidates/{id}") String report(@PathVariable Long id, Model m) {
    Candidate c = or404(candidates.findById(id));
    List<Submission> subs = submissions.findByCandidate(c);
    m.addAttribute("c", c); m.addAttribute("subs", subs);
    m.addAttribute("total", subs.stream().mapToInt(s -> s.marks).sum());
    m.addAttribute("maxMarks", c.exam.questions.stream().mapToInt(q -> q.marks).sum());
    m.addAttribute("vs", violations.findByCandidateOrderByOccurredAtDesc(c));
    m.addAttribute("parts", store.parts(c.sessionId));
    return "admin/report";
  }

  @GetMapping("/admin/recordings/{sid}/{part}") ResponseEntity<Resource> recording(@PathVariable String sid, @PathVariable long part) {
    or404(candidates.findBySessionId(sid)); // sid must be a real session => no path traversal
    Path p = store.file(sid, part);
    if (!Files.exists(p)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return ResponseEntity.ok().contentType(MediaType.parseMediaType("video/webm")).body(new FileSystemResource(p));
  }

  @GetMapping("/admin/exams/{id}/export.csv") ResponseEntity<String> csv(@PathVariable Long id) {
    Exam e = or404(exams.findById(id));
    StringBuilder sb = new StringBuilder("Candidate ID,Name,College,Roll No,Reg No,Email,Mobile,Course,Branch,Semester,IP,Browser,OS,Status,Started,Submitted,Marks,Violations\n");
    for (Candidate c : candidates.findByExamOrderByRegisteredAtDesc(e)) {
      int total = submissions.findByCandidate(c).stream().mapToInt(s -> s.marks).sum();
      sb.append(row(c.candidateId, c.name, c.college, c.rollNo, c.regNo, c.email, c.mobile, c.course, c.branch, c.semester,
          c.ip, c.browser, c.os, c.status, c.startedAt, c.submittedAt, total, c.violationCount)).append('\n');
    }
    return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report-" + id + ".csv")
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(sb.toString());
  }
  private static String row(Object... v) {
    return Arrays.stream(v).map(x -> {
      String s = x == null ? "" : String.valueOf(x);
      if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s; // CSV/Excel formula-injection guard
      return "\"" + s.replace("\"", "\"\"") + "\"";
    }).collect(Collectors.joining(","));
  }
}
