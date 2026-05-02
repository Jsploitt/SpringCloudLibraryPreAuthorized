package com.micro.bookservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micro.bookservice.models.UserNameChangedEvent;
import com.micro.bookservice.models.books.Book;
import com.micro.bookservice.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserEventListener {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BookService bookService;

    @Autowired
    public UserEventListener(BookService bookService) {
        this.bookService = bookService;
    }

    @KafkaListener(topics = "user-name-changes", groupId = "notification-group")
    public void handleUserCreated(String message) {
        try {
            UserNameChangedEvent event = objectMapper.readValue(message, UserNameChangedEvent.class);
            String userId = event.getId();
            String newFirstName = event.getFirstName();
            String newLastName = event.getLastName();

            // Fetch books by creator ID
            List<Book> books = bookService.getBooksByCreatorId(userId);
            if (books != null && !books.isEmpty()) {
                for (Book book : books) {
                    book.setCreatorFirstName(newFirstName);
                    book.setCreatorLastName(newLastName);
                    bookService.updateBook(book);
                    System.out.println("Updated book: " + book.getTitle() + " with new creator name: " + newFirstName + " " + newLastName);
                }
            } else {
                System.out.println("No books found for user " + userId);
            }

        } catch (JsonProcessingException e) {
            System.err.println("Failed to parse user event: " + e.getMessage());
        } catch (Exception ex) {
            System.err.println("Error updating books: " + ex.getMessage());
        }
    }
}

