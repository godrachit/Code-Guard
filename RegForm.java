package com.codeguard.model;
import jakarta.validation.constraints.*;
public class RegForm {
  @NotBlank @Size(max = 120) public String name;
  @NotBlank @Size(max = 160) public String college;
  @NotBlank @Size(max = 40) public String rollNo;
  @NotBlank @Size(max = 40) public String regNo;
  @NotBlank @Email @Size(max = 160) public String email;
  @NotBlank @Pattern(regexp = "[0-9+\\- ]{7,15}", message = "must be a valid phone number") public String mobile;
  @NotBlank @Size(max = 40) public String course;
  @NotBlank @Size(max = 80) public String branch;
  @NotBlank @Size(max = 20) public String semester;
  @Size(max = 30) public String screenRes;
  @Size(max = 30) public String language;
  @Size(max = 60) public String timezone;
}
