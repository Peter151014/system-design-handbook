package com.example.cloudfile.dto;

public record FileUploadedMessage(
        String eventId,
        String eventType,
        String bucket,
        String objectKey,
        String email
) {}