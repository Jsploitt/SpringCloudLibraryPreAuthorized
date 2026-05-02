package com.micro.bookservice.repository;

import com.micro.bookservice.models.books.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book,String> {
    public static final int MAX_BOOKS = 100;

    @Query("SELECT b FROM Book b WHERE b.genre = :genre")
    List<Book> findBooksByGenre(String genre);

    @Query("SELECT b FROM Book b WHERE b.author = :author")
    List<Book> findBooksByAuthor(String author);

    List<Book> findBooksByCreatorId(String userId);
}
