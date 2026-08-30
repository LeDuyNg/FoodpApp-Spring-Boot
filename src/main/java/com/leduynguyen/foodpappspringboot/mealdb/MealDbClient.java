package com.leduynguyen.foodpappspringboot.mealdb;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Thin client over TheMealDB's free v1 API. Deserialises the loose JSON into a
 * {@code Map} at the boundary and hands back a typed {@link Meal}.
 *
 * <p>The {@link RestClient} is built from an injected builder so tests can bind
 * a {@code MockRestServiceServer} to it.
 */
@Service
public class MealDbClient {

    private static final String BASE_URL = "https://www.themealdb.com/api/json/v1/1";

    private final RestClient restClient;

    public MealDbClient() {
        this(RestClient.builder());
    }

    /** For tests: pass a builder bound to a {@code MockRestServiceServer}. */
    MealDbClient(RestClient.Builder builder) {
        this.restClient = builder.baseUrl(BASE_URL).build();
    }

    public Meal fetchRandomMeal() {
        return Meal.from(firstMeal(get("/random.php")));
    }

    public Meal fetchMealById(String mealId) {
        return Meal.from(firstMeal(get("/lookup.php?i={id}", mealId)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String uri, Object... uriVariables) {
        return restClient.get().uri(uri, uriVariables).retrieve().body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstMeal(Map<String, Object> response) {
        Object meals = response == null ? null : response.get("meals");
        if (!(meals instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalStateException("Meal not found");
        }
        return (Map<String, Object>) list.get(0);
    }
}
