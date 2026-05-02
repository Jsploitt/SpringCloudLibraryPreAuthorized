package com.micro.bookservice.models.books;

import jakarta.persistence.Entity;

@Entity
public class EBook extends WrittenBook  {
    private int size;

    public EBook() {
        super();
    }

    public EBook(String title,String author, String genre,String ISBN, int numOfPages,int size) {
        super(title, author, genre, ISBN, numOfPages);
        this.size = size;
    }

    public int getSize() {
        return size;
    }

}
