package com.micro.bookservice.service;

import com.micro.bookservice.models.books.Book;
import com.micro.bookservice.repository.BookRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;


@Service
public class BookService {
    @Autowired
    private BookRepository bookRepository;

//    public BookService() {
////        this.addBook(new PrintedBook("Title B", "Author B", "SCI-FI", "1112", 200));
//
//    }

    public Book getBookByISBN(String ISBN) {
        Optional<Book> book = bookRepository.findById(ISBN);
        if (book.isPresent())
            return book.get();
        else
            return null; // or throw an exception, or return a default book
    }

    public Book[] getAllBooks() {
        return bookRepository.findAll().toArray(new Book[0]);
    }

    public List<Book> getBookByGenre(String genre) {
        return bookRepository.findBooksByGenre(genre);
    }

    public  List<Book>  getBookByAuthor(String author) {
        return bookRepository.findBooksByAuthor(author);
    }

    public int getNumberOfBooks() {
        return (int) bookRepository.count();
    }

    public boolean deleteBook(String isbn) {
        if (bookRepository.existsById(isbn)) {
            bookRepository.deleteById(isbn);
            return true;
        }
        return false;
    }

    public int addBook(Book book) {
        if (bookRepository.existsById(book.getISBN())) {
            return -1; // Book already exists
        }
        if (bookRepository.count() >= BookRepository.MAX_BOOKS) {
            return 1; // Library is full
        }
        bookRepository.save(book);
        return 0; // Book added successfully
    }

    public List<Book> getBookByGenreAndAuthor(String genre, String author) {
        List<Book> booksByGenre = bookRepository.findBooksByGenre(genre);
        List<Book> booksByAuthor = bookRepository.findBooksByAuthor(author);

        // Find intersection of both lists
        booksByGenre.retainAll(booksByAuthor);

        return booksByGenre;
    }

    public List<Book> getBooksByCreatorId(String userId) {
        List<Book> books = bookRepository.findBooksByCreatorId(userId);
        if (books.isEmpty()) {
            return null; // or throw an exception, or return an empty list
        }
        return books;
    }

    public void updateBook(Book book) {
        Optional<Book> existingBookOptional = bookRepository.findById(book.getISBN());
        if (existingBookOptional.isPresent()) {
            Book existingBook = existingBookOptional.get();
            // Update fields as necessary
            existingBook.setTitle(book.getTitle());
            existingBook.setAuthor(book.getAuthor());
            existingBook.setGenre(book.getGenre());
            existingBook.setCreatorId(book.getCreatorId());
            existingBook.setCreatorFirstName(book.getCreatorFirstName());
            existingBook.setCreatorLastName(book.getCreatorLastName());
            bookRepository.save(existingBook);
        } else {
            throw new RuntimeException("Book not found with ISBN: " + book.getISBN());
        }
    }
}
