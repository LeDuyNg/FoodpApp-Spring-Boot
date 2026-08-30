package com.leduynguyen.foodpappspringboot.mealdb;

/**
 * One line of a meal's ingredient list, e.g. measure {@code "1 cup"} + name
 * {@code "Flour"}. TheMealDB sometimes gives a name with no measure.
 */
public record Ingredient(String measure, String name) {

    /** {@code "1 cup Flour"}, or just {@code "Flour"} when there is no measure. */
    public String line() {
        String trimmedName = name.trim();
        return (measure == null || measure.isBlank())
                ? trimmedName
                : measure.trim() + " " + trimmedName;
    }
}
