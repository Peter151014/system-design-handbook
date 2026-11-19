package com.example.cloudfile.service;

import com.example.cloudfile.dto.FileUploadedMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import org.springframework.stereotype.Service;

@Service
public class SqsConsumer {

    private final EmailService emailService;

    public SqsConsumer(EmailService emailService) {
        this.emailService = emailService;
    }

    @SqsListener("${aws.sqs.queue-name}")
    public void consumeMessage(FileUploadedMessage message) {

        System.out.println("Message received from SQS");

        emailService.sendUploadNotification(
                message.email(),
                message.objectKey()
        );
    }
}