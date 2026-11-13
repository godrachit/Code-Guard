package com.codeguard.service;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Recordings live outside the web root and are only served through an ADMIN-only controller. */
@Component
public class RecordingStore {
  private final Path root;
  public RecordingStore(@Value("${codeguard.storage}") String dir) throws IOException {
    root = Path.of(dir).toAbsolutePath().normalize();
    Files.createDirectories(root);
  }
  public Path file(String sid, long part) { return root.resolve(sid + "-" + part + ".webm"); }
  public List<Long> parts(String sid) {
    try (var s = Files.list(root)) {
      return s.map(p -> p.getFileName().toString())
          .filter(n -> n.startsWith(sid + "-") && n.endsWith(".webm"))
          .map(n -> Long.parseLong(n.substring(sid.length() + 1, n.length() - 5))).sorted().toList();
    } catch (IOException e) { return List.of(); }
  }
}
