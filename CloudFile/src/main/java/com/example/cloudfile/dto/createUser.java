package com.example.cloudfile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class createUser {

    @NotBlank
    private String name;

    @NotBlank
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;
}
