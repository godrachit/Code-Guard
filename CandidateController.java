package com.codeguard.web;
import com.codeguard.model.*;
import com.codeguard.repo.*;
import com.codeguard.util.Util;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class CandidateController {
  private static final String TEMPLATE = """
      import java.util.*;

      public class Main {
          public static void main(String[] args) {
              Scanner sc = new Scanner(System.in);
              // write your solution here
          }
      }
      """;
  private final ExamRepository exams; private final CandidateRepository candidates; private final SubmissionRepository submissions;
  public CandidateController(ExamRepository e, CandidateRepository c, SubmissionRepository s) { exams = e; candidates = c; submissions = s; }

  @InitBinder("form") void binder(WebDataBinder b) { b.initDirectFieldAccess(); }

  private Exam validExam(String token) {
    return exams.findByToken(token)
        .filter(e -> e.linkActive && (e.expiresAt == null || e.expiresAt.isAfter(LocalDateTime.now())))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
  private Candidate cand(String sid) { return candidates.findBySessionId(sid).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }

  @GetMapping("/test/{token}") String form(@PathVariable String token, Model m) {
    m.addAttribute("exam", validExam(token)); m.addAttribute("form", new RegForm());
    return "candidate/register";
  }

  @PostMapping("/test/{token}/register")
  String register(@PathVariable String token, @Valid @ModelAttribute("form") RegForm f, BindingResult br,
                  @RequestParam(required = false) String consent, HttpServletRequest req, Model m) {
    Exam e = validExam(token);
    List<String> errors = new ArrayList<>();
    br.getFieldErrors().forEach(x -> errors.add(x.getField() + ": " + x.getDefaultMessage()));
    if (consent == null) errors.add("You must accept the data-collection notice");
    else if (errors.isEmpty() && (candidates.existsByExamAndEmailIgnoreCase(e, f.email) || candidates.existsByExamAndRollNoIgnoreCase(e, f.rollNo)))
      errors.add("This email or roll number is already registered for this test");
    if (!errors.isEmpty()) { m.addAttribute("exam", e); m.addAttribute("errors", errors); return "candidate/register"; }

    Candidate c = new Candidate();
    c.exam = e; c.candidateId = "CG-" + Util.token(8); c.sessionId = UUID.randomUUID().toString();
    c.name = f.name.trim(); c.college = f.college.trim(); c.rollNo = f.rollNo.trim(); c.regNo = f.regNo.trim();
    c.email = f.email.trim(); c.mobile = f.mobile.trim(); c.course = f.course.trim(); c.branch = f.branch.trim(); c.semester = f.semester.trim();
    String ua = req.getHeader("User-Agent");
    c.userAgent = ua == null ? null : Util.cut(ua, 400);
    c.browser = Util.browser(ua); c.os = Util.os(ua); c.deviceType = Util.device(ua);
    c.ip = req.getRemoteAddr(); // server-side; proxy headers honoured only from trusted proxies (forward-headers-strategy=native)
    c.screenRes = f.screenRes; c.language = f.language; c.timezone = f.timezone;
    candidates.save(c);
    return "redirect:/session/" + c.sessionId + "/ready";
  }

  @GetMapping("/session/{sid}/ready") String ready(@PathVariable String sid, Model m) {
    Candidate c = cand(sid);
    if ("SUBMITTED".equals(c.status)) return "redirect:/session/" + sid + "/done";
    m.addAttribute("c", c);
    return "candidate/ready";
  }

  @GetMapping("/session/{sid}/exam") String exam(@PathVariable String sid, Model m) {
    Candidate c = cand(sid);
    if ("SUBMITTED".equals(c.status)) return "redirect:/session/" + sid + "/done";
    Map<Long, String> saved = new HashMap<>();
    for (Submission s : submissions.findByCandidate(c)) saved.put(s.question.id, s.code);
    m.addAttribute("c", c); m.addAttribute("exam", c.exam); m.addAttribute("saved", saved); m.addAttribute("template", TEMPLATE);
    return "candidate/exam";
  }

  @GetMapping("/session/{sid}/done") String done(@PathVariable String sid, Model m) { m.addAttribute("c", cand(sid)); return "candidate/done"; }
}
