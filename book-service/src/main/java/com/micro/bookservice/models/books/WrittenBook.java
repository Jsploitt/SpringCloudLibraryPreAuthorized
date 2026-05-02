package com.micro.bookservice.models.books;

import jakarta.persistence.Entity;

@Entity
public abstract class WrittenBook extends Book {
    private int numOfPages;

    public WrittenBook(String title,String author, String genre,String ISBN, int numOfPages) {
        super(title, author, ISBN, genre);
        this.numOfPages = numOfPages;
    }

    public WrittenBook() {
        super();
    }

    public int getNumOfPages() {
        return numOfPages;
    }
}
