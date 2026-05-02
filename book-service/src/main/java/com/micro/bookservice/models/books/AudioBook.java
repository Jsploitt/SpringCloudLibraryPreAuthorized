package com.micro.bookservice.models.books;

import jakarta.persistence.Entity;

@Entity
public class AudioBook extends Book  {
    private int duration;
    private String narrator;

    public AudioBook() {

    }

    public AudioBook(String title, String author, String genre,String ISBN, int duration, String narrator) {
        super(title, author, ISBN, genre);
        this.duration = duration;
        this.narrator = narrator;
    }



    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public String getNarrator() {
        return narrator;
    }

    public void setNarrator(String narrator) {
        this.narrator = narrator;
    }

}
