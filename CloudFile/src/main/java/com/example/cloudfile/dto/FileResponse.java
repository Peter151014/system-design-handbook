package com.example.cloudfile.dto;

import jakarta.persistence.PrePersist;

import java.time.LocalDateTime;

public record FileResponse(
        Long id,
        String originalFileName,
        String objectKey,
        String contentType,
        Long fileSize,
        LocalDateTime uploadedAt
) {

}
