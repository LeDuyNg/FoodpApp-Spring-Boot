package com.leduynguyen.foodpappspringboot.mealdb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link MealDbClient} against a mocked HTTP endpoint - checks the URLs it
 * calls, that a canned body maps to a {@link Meal}, and that TheMealDB's
 * {@code {"meals": null}} "not found" shape is turned into an exception.
 */
class MealDbClientTest {

    private static final String BASE = "https://www.themealdb.com/api/json/v1/1";

    private MockRestServiceServer server;
    private MealDbClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MealDbClient(builder);
    }

    private static String papayaSaladJson() throws Exception {
        try (InputStream in = MealDbClientTest.class.getResourceAsStream("/mealdb/papaya-salad.json")) {
            return new String(in.readAllBytes());
        }
    }

    @Test
    void fetchRandomMeal_callsRandomEndpointAndMapsTheBody() throws Exception {
        server.expect(requestTo(BASE + "/random.php"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(papayaSaladJson(), MediaType.APPLICATION_JSON));

        Meal meal = client.fetchRandomMeal();

        assertThat(meal.title()).isEqualTo("Papaya salad");
        assertThat(meal.ingredients()).hasSize(8);
        server.verify();
    }

    @Test
    void fetchMealById_expandsTheIdIntoTheQueryString() throws Exception {
        server.expect(requestTo(BASE + "/lookup.php?i=53573"))
                .andRespond(withSuccess(papayaSaladJson(), MediaType.APPLICATION_JSON));

        assertThat(client.fetchMealById("53573").id()).isEqualTo("53573");
        server.verify();
    }

    @Test
    void fetchMealById_throwsWhenTheMealDbReturnsNoMeals() {
        server.expect(requestTo(BASE + "/lookup.php?i=00000"))
                .andRespond(withSuccess("{\"meals\":null}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchMealById("00000"))
                .isInstanceOf(IllegalStateException.class);
    }
}
