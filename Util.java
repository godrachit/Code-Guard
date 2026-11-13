package com.codeguard.util;
import java.security.SecureRandom;
public final class Util {
  private static final SecureRandom RND = new SecureRandom();
  private static final String ALPHA = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 32 chars = 5 bits each
  private Util() {}
  public static String token(int n) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < n; i++) b.append(ALPHA.charAt(RND.nextInt(ALPHA.length())));
    return b.toString();
  }
  public static String cut(String s, int max) { return s == null ? "" : s.length() <= max ? s : s.substring(0, max); }
  public static String browser(String ua) {
    if (ua == null) return "Unknown";
    if (ua.contains("Edg/")) return "Edge " + ver(ua, "Edg/");
    if (ua.contains("OPR/")) return "Opera " + ver(ua, "OPR/");
    if (ua.contains("Firefox/")) return "Firefox " + ver(ua, "Firefox/");
    if (ua.contains("Chrome/")) return "Chrome " + ver(ua, "Chrome/");
    if (ua.contains("Safari/")) return "Safari " + ver(ua, "Version/");
    return "Unknown";
  }
  private static String ver(String ua, String t) {
    int i = ua.indexOf(t); if (i < 0) return "";
    String r = ua.substring(i + t.length()); int e = 0;
    while (e < r.length() && (Character.isDigit(r.charAt(e)) || r.charAt(e) == '.')) e++;
    return r.substring(0, e);
  }
  public static String os(String ua) {
    if (ua == null) return "Unknown";
    if (ua.contains("Windows")) return "Windows";
    if (ua.contains("Android")) return "Android";
    if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
    if (ua.contains("Mac OS X")) return "macOS";
    if (ua.contains("CrOS")) return "ChromeOS";
    if (ua.contains("Linux")) return "Linux";
    return "Unknown";
  }
  public static String device(String ua) {
    if (ua == null) return "Unknown";
    if (ua.contains("iPad") || ua.contains("Tablet")) return "Tablet";
    return ua.contains("Mobi") || ua.contains("Android") ? "Mobile" : "Desktop";
  }
}
