package com.soorinote.einknote;

public class Note {
    public long id;
    public String title;
    public String content;
    public String updatedAt;

    public Note(long id, String title, String content, String updatedAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.updatedAt = updatedAt;
    }
}