package com.micro.bookservice.controller;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micro.bookservice.models.books.Book;
import com.micro.bookservice.service.BookService;
import com.micro.bookservice.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RequestMapping("/book")
@RestController
public class BookController {

    private final BookService bookService;
    private final JwtService jwtService;
    private final RestTemplate restTemplate;

    @Autowired
    public BookController(BookService bookService, JwtService jwtService, RestTemplate restTemplate) {
        this.bookService = bookService;
        this.jwtService = jwtService;
        this.restTemplate = restTemplate;
    }

//    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{ISBN}")
    public ResponseEntity<?> getBook(@PathVariable String ISBN, HttpServletRequest request) {
        String status = getUserStatus(request);
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "User account is not active. Access denied."));
        }


        Object res = bookService.getBookByISBN(ISBN);
        if (res != null) {
            return ResponseEntity.ok(res);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No book found with ISBN: " + ISBN));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllBooks(HttpServletRequest request) {
        String status = getUserStatus(request);
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "User account is not active. Access denied."));
        }

        Object res = bookService.getAllBooks();
        if (res == null || ((Book[]) res).length == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No books found in the library."));
        }
        return ResponseEntity.ok(
                Arrays.stream((Book[]) res)
                        .filter(Objects::nonNull)
                        .toArray(Book[]::new)
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/add")
    public ResponseEntity<?> addBook(@RequestBody Book book, HttpServletRequest request) {
        String status = getUserStatus(request);
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "User account is not active. Access denied."));
        }

        if (book.verifyISBN(book.getISBN())) {
            if (book.getAuthor().length() < 2) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Invalid author name: " + book.getAuthor() + ". Author name must be at least 2 characters long."));
            }
            if (book.getGenre().length() < 2) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Invalid book genre: " + book.getGenre() + ". Book genre must be at least 2 characters long."));
            }

            String authHeader = request.getHeader("Authorization");
            String token = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7); // Remove "Bearer " prefix
            }

            String creatorId = jwtService.extractUUID(token);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    "http://user-service:8085/auth/user/" + creatorId,
                    HttpMethod.GET,
                    httpEntity,
                    String.class
            );

            String firstName = null ;
            String lastName = null ;
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode json = mapper.readTree(response.getBody());
                     firstName = json.get("firstName").asText();
                     lastName = json.get("lastName").asText();

                } catch (JsonProcessingException e) {
                    // Handle the parsing error
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("message", e.getMessage()));
                }
            }

            book.setCreatorId(creatorId);
            book.setCreatorFirstName(firstName);
            book.setCreatorLastName(lastName);
            book.generateReference();

            int bookStatus = bookService.addBook(book);
            if (bookStatus == 0) {


                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(Map.of("message", "The book " + book.getTitle() + " has been added to the library."));

            } else if (bookStatus > 0) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "The library is full you must delete a book first"));
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "The book with ISBN: " + book.getISBN() + " already exists in the library."));
            }
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid ISBN: " + book.getISBN() + ". Please provide a valid ISBN."));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{ISBN}")
    public ResponseEntity<?> deleteBook(@PathVariable String ISBN,HttpServletRequest request) {
        String status = getUserStatus(request);
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "User account is not active. Access denied."));
        }

        boolean deleted = bookService.deleteBook(ISBN);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "Book with ISBN " + ISBN + " has been deleted."));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No book with ISBN " + ISBN + " exists."));
        }
    }

    @GetMapping("/filter")
    public ResponseEntity<?> filterBooks(
            @RequestParam(value = "genre", required = false) String genre,
            @RequestParam(value = "author", required = false) String author,
            HttpServletRequest request) {
        String status = getUserStatus(request);
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "User account is not active. Access denied."));
        }


        if (genre != null && author != null) {

            List<Book> bookList = bookService.getBookByGenreAndAuthor(genre, author);
            if (!bookList.isEmpty()) {
                return ResponseEntity.ok(bookList);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No books found for the genre: " + genre + " and author: " + author));
            }

        } else if (genre != null) {

            List<Book> bookList = bookService.getBookByGenre(genre);
            if (!bookList.isEmpty()) {
                return ResponseEntity.ok(bookList);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No books found for the genre: " + genre));
            }

        } else if (author != null) {

            List<Book> bookList = bookService.getBookByAuthor(author);
            if (!bookList.isEmpty()) {
                return ResponseEntity.ok(bookList);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "No books found for the author: " + author));
            }

        } else {

            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Please provide at least one filter parameter: genre or author."));
        }
    }

    @GetMapping("/total")
    public ResponseEntity<?> getTotalBooks(HttpServletRequest request) {
        String status = getUserStatus(request);
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "User account is not active. Access denied."));
        }

        int number = bookService.getNumberOfBooks();
        if (number > 0) {
            return ResponseEntity.ok(Map.of("total", number));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No books found in the library.", "total", 0));
        }
    }

    private String getUserStatus(HttpServletRequest request) {

        String authHeader = request.getHeader("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7); // Remove "Bearer " prefix
        }

        String userId = jwtService.extractUUID(token);

        // Set Authorization header for the outgoing request
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://user-service/auth/user/" + userId,
                HttpMethod.GET,
                httpEntity,
                String.class
        );

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode json = mapper.readTree(response.getBody());
                return json.get("status").asText();
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to parse user status from user-service");
            }
        }
        throw new RuntimeException("Failed to retrieve user status from user-service");
    }



}
