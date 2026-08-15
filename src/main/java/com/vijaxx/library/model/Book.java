package com.vijaxx.library.model;

import java.util.Objects;

/** A title held by the library, with its copy counts. */
public class Book {

    private int id;
    private String isbn;
    private String title;
    private String author;
    private String category;
    private int totalCopies;
    private int availableCopies;

    public Book() {
    }

    public Book(int id, String isbn, String title, String author, String category,
                int totalCopies, int availableCopies) {
        this.id = id;
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.category = category;
        this.totalCopies = totalCopies;
        this.availableCopies = availableCopies;
    }

    public static Book of(String isbn, String title, String author, String category, int totalCopies) {
        return new Book(0, isbn, title, author, category, totalCopies, totalCopies);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getTotalCopies() { return totalCopies; }
    public void setTotalCopies(int totalCopies) { this.totalCopies = totalCopies; }

    public int getAvailableCopies() { return availableCopies; }
    public void setAvailableCopies(int availableCopies) { this.availableCopies = availableCopies; }

    /** Copies currently in members' hands. */
    public int getBorrowedCopies() {
        return totalCopies - availableCopies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Book other)) return false;
        return id == other.id && Objects.equals(isbn, other.isbn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, isbn);
    }

    @Override
    public String toString() {
        return title + " (" + isbn + ")";
    }
}
