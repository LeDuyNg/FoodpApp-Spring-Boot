package com.leduynguyen.foodpappspringboot.dto;

import jakarta.validation.constraints.NotBlank;

/** A single comment left on a recipe. Must not be blank. */
public class CommentForm {
    @NotBlank
    private String comment;

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
