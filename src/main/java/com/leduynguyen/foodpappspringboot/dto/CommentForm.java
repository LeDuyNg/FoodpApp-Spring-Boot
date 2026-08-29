package com.leduynguyen.foodpappspringboot.dto;

import jakarta.validation.constraints.NotBlank;

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
