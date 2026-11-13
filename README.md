# CodeGuard (prototype)
Java-only online coding assessment + proctoring prototype. Spring Boot 3, Thymeleaf, Spring Security, JPA, WebSocket.

## Run
Requirements: **JDK 17+ (a full JDK, not a JRE)** and Maven 3.9+.
    mvn spring-boot:run
Open http://localhost:8080/admin  (user `admin`, password from env `ADMIN_PASSWORD`, default `ChangeMe123!`).
MySQL instead of the embedded H2 DB:  `mvn spring-boot:run -Dspring-boot.run.profiles=mysql` (set DB_USER / DB_PASSWORD).

## Try the workflow
1. Admin: log in -> New test -> add a question (Java, class `Main`, stdin/stdout) -> copy the candidate link.
2. Candidate (Chrome/Edge, `http://localhost` or HTTPS): open link -> register -> Continue -> share ENTIRE screen -> code -> Run -> Submit.
3. Admin: Live monitor (updates over WebSocket), candidate report (code, marks, violations, recordings), Export CSV.

## Prototype limits (be honest before real use)
- **Code runner is not a sandbox.** It runs candidate code in a separate JVM with time/heap/output/concurrency limits only. Replace `CodeRunner` with a Docker/gVisor runner before using real candidates.
- Screen capture needs HTTPS on any host other than localhost. The browser decides what is shareable; non-"entire screen" choices are detected and rejected where the browser reports it.
- Browser monitoring can be bypassed; flagged events are signals for human review only.
- The single browser-only JS file is `static/js/bridge.js` (+ `monitor.js` for admin refresh). All logic is Java.
- Not included yet: editing a test, Flyway migrations, rate limiting, audit_logs table, retention jobs, live stream viewing, charts, manual mark override, multi-language runners.
- Recordings are WebM chunks in `./data/recordings` (no seek index; each resume starts a new file). Admin-only access.
- Admin account is a single BCrypt-hashed in-memory user; move to a table for multiple admins.
