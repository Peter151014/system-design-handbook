package com.example.cloudfile.service;

import com.example.cloudfile.dto.FileUploadedMessage;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SqsProducer {

    private final SqsTemplate sqsTemplate;

    @Value("${aws.sqs.queue-name}")
    private String queueName;

    public SqsProducer(SqsTemplate sqsTemplate) {
        this.sqsTemplate = sqsTemplate;
    }

    public void sendMessage(FileUploadedMessage message){

        sqsTemplate.send(queueName,message);

        System.out.println("Message Successfully sent to SQS");
    }
}
