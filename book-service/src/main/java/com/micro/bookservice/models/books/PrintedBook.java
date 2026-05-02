package com.micro.bookservice.models.books;

import jakarta.persistence.Entity;

@Entity
public class PrintedBook extends WrittenBook  {
    public boolean hardcover;

    public PrintedBook() {
        super();
    }

    public PrintedBook(String title,String author, String genre,String ISBN, int numOfPages) {
        super(title,author,genre,ISBN,numOfPages);
        this.hardcover = false;
    }
    public PrintedBook(String title,String author, String genre,String ISBN, int numOfPages,boolean hardcover) {
        super(title,author,genre,ISBN,numOfPages);
        this.hardcover = hardcover;
    }
    public String getCoverType() {
        if (hardcover) {
            return "hardcover";
        }
        return "paperback";

    }

}

