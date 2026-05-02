package com.micro.bookservice.models.books;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.persistence.*;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "bookType")
@JsonSubTypes({
        @JsonSubTypes.Type(value=PrintedBook.class, name = "PrintedBook"),
        @JsonSubTypes.Type(value= AudioBook.class, name = "AudioBook"),
        @JsonSubTypes.Type(value=EBook.class, name = "EBook")
})
//
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
//@MappedSuperclass
public abstract class Book  {
    @Id
    private String ISBN;
    private String title;
    private String author;
    private String genre;
    private String refCode;
    private String creatorFirstName;
    private String creatorLastName;
    @Column(length = 36, columnDefinition = "varchar(36)")
    private String creatorId;


    public Book() {

    }

    public Book(String title, String author, String ISBN, String genre)   {
        this.ISBN = ISBN;
        this.title = title;
        this.author = author;
        this.genre = genre;
    }
    public void generateReference(){
        this.refCode = String.format("%s-%s", author.substring(0, 2).toUpperCase(), genre.substring(0, 2).toUpperCase());
    }
    public boolean verifyISBN(String ISBN){
//        How to verify an ISBN?
//        Check that the string  is if length 4 and only contains digits
        if  (!ISBN.matches("[0-9]+") || ISBN.length() != 4){
            return false;
        }
//        Given ISBN = n1n2n3n4
//        the formula for checking correctness is as follows:
//        ( n1 × 3 + n2 × 2 + n3 × 1) mod 4 = n4
        int tempISBN = Integer.parseInt(ISBN);
        int myArray[] = new int[4];
        for (int i = 0; i < 4; i++) {
            myArray[i] = tempISBN % 10;
            tempISBN = tempISBN / 10;
        }
        if ((myArray[3] * 3 + myArray[2] * 2 + myArray[1]) % 4 == myArray[0] ) {
            return true;
        }
        return false;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getAuthor() {
        return author;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getGenre() {
        return genre;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setISBN(String ISBN) {
        this.ISBN = ISBN;
    }

    public String getISBN() {
        return ISBN;
    }

    public String getRefCode() {
        return refCode;
    }

    public String getCreatorFirstName() {
        return creatorFirstName;
    }
    public void setCreatorFirstName(String creatorFirstName) {
        this.creatorFirstName = creatorFirstName;
    }
    public String getCreatorLastName() {
        return creatorLastName;
    }
    public void setCreatorLastName(String creatorLastName) {
        this.creatorLastName = creatorLastName;
    }
    public String getCreatorId() {
        return creatorId;
    }
    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }
}
