package com.micro.bookservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micro.bookservice.models.UserNameChangedEvent;
import com.micro.bookservice.models.books.Book;
import com.micro.bookservice.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

@Component
public class UserEventListener {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BookService bookService;
    private final SqsClient sqsClient;

    @Value("${aws.sqs.queue-url}")
    private String queueUrl;

    @Autowired
    public UserEventListener(BookService bookService, SqsClient sqsClient) {
        this.bookService = bookService;
        this.sqsClient = sqsClient;
    }

    @Scheduled(fixedDelayString = "${sqs.poll-interval-ms:5000}")
    public void pollQueue() {
        try {
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(20)
                    .build();

            List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();
            for (Message message : messages) {
                processMessage(message.body());
                sqsClient.deleteMessage(del -> del
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build());
            }
        } catch (Exception e) {
            System.err.println("Error polling SQS queue: " + e.getMessage());
        }
    }

    private void processMessage(String body) {
        try {
            UserNameChangedEvent event = objectMapper.readValue(body, UserNameChangedEvent.class);
            List<Book> books = bookService.getBooksByCreatorId(event.getId());
            if (books != null && !books.isEmpty()) {
                for (Book book : books) {
                    book.setCreatorFirstName(event.getFirstName());
                    book.setCreatorLastName(event.getLastName());
                    bookService.updateBook(book);
                    System.out.println("Updated book: " + book.getTitle()
                            + " — new creator: " + event.getFirstName() + " " + event.getLastName());
                }
            }
        } catch (JsonProcessingException e) {
            System.err.println("Failed to parse user-name-change event: " + e.getMessage());
        }
    }
}
