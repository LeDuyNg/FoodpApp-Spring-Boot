package com.leduynguyen.foodpappspringboot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RecipeForm {
    @NotBlank @Size(min = 1, max = 80)
    private String title;

    @NotBlank
    private String description;

    @NotBlank
    private String ingredients;

    @NotBlank
    private String instructions;

    private String temperature;
    private String dishType;
    private String dairy;
    private String sweetness;
    private String meat;
    private String seafood;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIngredients() {
        return ingredients;
    }

    public void setIngredients(String ingredients) {
        this.ingredients = ingredients;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getDishType() {
        return dishType;
    }

    public void setDishType(String dishType) {
        this.dishType = dishType;
    }

    public String getSweetness() {
        return sweetness;
    }

    public void setSweetness(String sweetness) {
        this.sweetness = sweetness;
    }

    public String getSeafood() {
        return seafood;
    }

    public void setSeafood(String seafood) {
        this.seafood = seafood;
    }

    public String getMeat() {
        return meat;
    }

    public void setMeat(String meat) {
        this.meat = meat;
    }

    public String getDairy() {
        return dairy;
    }

    public void setDairy(String dairy) {
        this.dairy = dairy;
    }

    public String getTemperature() {
        return temperature;
    }

    public void setTemperature(String temperature) {
        this.temperature = temperature;
    }
}
