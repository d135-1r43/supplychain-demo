package net.herhoffer;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;

@QuarkusTest
class SpeakerResourceTest {

    @Test
    void listsSeededSpeakers() {
        given()
                .when().get("/api/speakers")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(3))
                .body("name", hasItem("Anna Bergmann"));
    }

    @Test
    void filtersByCompany() {
        given()
                .queryParam("company", "Freelance")
                .when().get("/api/speakers")
                .then()
                .statusCode(200)
                .body("company", everyItem(is("Freelance")));
    }

    @Test
    void createsAndDeletesASpeaker() {
        int id = given()
                .contentType("application/json")
                .body(Map.of("name", "Jonas Kirchner", "company", "Isarwerk GmbH", "bio", "Talks about builds."))
                .when().post("/api/speakers")
                .then()
                .statusCode(201)
                .body("name", is("Jonas Kirchner"))
                .extract().path("id");

        given().when().get("/api/speakers/" + id).then().statusCode(200);
        given().when().delete("/api/speakers/" + id).then().statusCode(204);
        given().when().get("/api/speakers/" + id).then().statusCode(404);
    }
}
