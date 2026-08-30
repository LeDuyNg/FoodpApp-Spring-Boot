package com.leduynguyen.foodpappspringboot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** A 1-5 star rating submitted for a recipe. One rating per user per recipe. */
public class RatingForm {
    @Min(1) @Max(5)
    private int rating;

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }
}
