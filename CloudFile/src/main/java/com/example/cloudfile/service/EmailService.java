
package com.example.cloudfile.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

@Service
public class EmailService {

    private final SesV2Client sesClient;

    @Value("${aws.ses.sender-email}")
    private String senderEmail;

    public EmailService(SesV2Client sesClient) {
        this.sesClient = sesClient;
    }

    public void sendUploadNotification(String recipientEmail,
                                       String fileName) {

        String subject = "CloudFile - File Uploaded Successfully";

        String body = """
                Hello,

                Your file '%s' has been uploaded successfully to CloudFile.

                Thank you for using CloudFile!
                """.formatted(fileName);

        Destination destination = Destination.builder()
                .toAddresses(recipientEmail)
                .build();

        Content subjectContent = Content.builder()
                .data(subject)
                .build();

        Content bodyContent = Content.builder()
                .data(body)
                .build();

        Body emailBody = Body.builder()
                .text(bodyContent)
                .build();

        Message message = Message.builder()
                .subject(subjectContent)
                .body(emailBody)
                .build();

        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(senderEmail)
                .destination(destination)
                .content(EmailContent.builder()
                        .simple(message)
                        .build())
                .build();

        sesClient.sendEmail(request);

        System.out.println("Email successfully sent to: " + recipientEmail);
    }
}
