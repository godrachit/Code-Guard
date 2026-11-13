package com.codeguard.service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;

/**
 * PROTOTYPE runner: compiles and runs candidate Java code in a SEPARATE JVM process with a time limit,
 * heap limit, output cap and concurrency cap. It is NOT a security sandbox. For real candidates,
 * replace this class with a call to a Docker/gVisor/Firecracker runner (no network, read-only FS, non-root).
 */
@Service
public class CodeRunner {
  private record Out(int exit, String text, boolean timedOut) {}
  private final Semaphore slots = new Semaphore(4);

  public String run(String code, String stdin) {
    try { if (!slots.tryAcquire(30, TimeUnit.SECONDS)) return "SERVER_BUSY: try again"; }
    catch (InterruptedException e) { Thread.currentThread().interrupt(); return "INTERRUPTED"; }
    Path dir = null;
    try {
      dir = Files.createTempDirectory("cg-run");
      Files.writeString(dir.resolve("Main.java"), code == null ? "" : code);
      String bin = Path.of(System.getProperty("java.home"), "bin").toString();
      Out c = exec(dir, 15, null, Path.of(bin, "javac").toString(), "-encoding", "UTF-8", "Main.java");
      if (c.timedOut() || c.exit() != 0) return "COMPILE_ERROR\n" + c.text();
      Out r = exec(dir, 5, stdin == null ? "" : stdin.replace("\r", ""), Path.of(bin, "java").toString(),
          "-Xmx128m", "-Xss1m", "-XX:+UseSerialGC", "-cp", ".", "Main");
      if (r.timedOut()) return "TIME_LIMIT_EXCEEDED\n" + r.text();
      if (r.exit() != 0) return "RUNTIME_ERROR\n" + r.text();
      return r.text();
    } catch (Exception e) {
      return "RUNNER_ERROR: " + e.getMessage() + " (a full JDK is required, not just a JRE)";
    } finally {
      slots.release();
      if (dir != null) delete(dir);
    }
  }

  private Out exec(Path dir, long secs, String stdin, String... cmd) throws Exception {
    ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true);
    pb.environment().keySet().removeAll(List.of("ADMIN_PASSWORD", "DB_PASSWORD", "DB_USER"));
    Process p = pb.start();
    CompletableFuture<String> reader = CompletableFuture.supplyAsync(() -> {
      try { return new String(p.getInputStream().readNBytes(10_000), StandardCharsets.UTF_8); }
      catch (IOException e) { return ""; }
    });
    if (stdin != null) {
      try (OutputStream os = p.getOutputStream()) { os.write(stdin.getBytes(StandardCharsets.UTF_8)); }
      catch (IOException ignored) {}
    }
    boolean done = p.waitFor(secs, TimeUnit.SECONDS);
    if (!done) p.destroyForcibly();
    String text;
    try { text = reader.get(3, TimeUnit.SECONDS); } catch (Exception e) { text = ""; }
    return new Out(done ? p.exitValue() : -1, text, !done);
  }

  private static void delete(Path d) {
    try (var w = Files.walk(d)) { w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); }
    catch (IOException ignored) {}
  }
}
