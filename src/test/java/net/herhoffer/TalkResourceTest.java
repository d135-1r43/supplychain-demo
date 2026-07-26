package net.herhoffer;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class TalkResourceTest {

    @Test
    void listsSeededTalksWithTheirSpeaker() {
        given()
                .when().get("/api/talks")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(4))
                .body("[0].title", notNullValue())
                .body("speaker.name", everyItem(notNullValue()));
    }

    @Test
    void filtersByRoom() {
        given()
                .queryParam("room", "Hoersaal 2")
                .when().get("/api/talks")
                .then()
                .statusCode(200)
                .body("room", everyItem(is("Hoersaal 2")));
    }

    @Test
    void returnsNotFoundForUnknownTalk() {
        given()
                .when().get("/api/talks/9999")
                .then()
                .statusCode(404);
    }

    @Test
    void createsUpdatesAndDeletesATalk() {
        int id = given()
                .contentType("application/json")
                .body(Map.of(
                        "title", "Reproducible Builds",
                        "summary", "Same input, same output, eventually.",
                        "durationMinutes", 45,
                        "scheduledAt", "2026-11-19T19:00:00",
                        "room", "Hoersaal 1",
                        "speakerId", 1))
                .when().post("/api/talks")
                .then()
                .statusCode(201)
                .body("title", is("Reproducible Builds"))
                .body("speaker.id", is(1))
                .extract().path("id");

        given()
                .contentType("application/json")
                .body(Map.of(
                        "title", "Reproducible Builds",
                        "summary", "Same input, same output, eventually.",
                        "durationMinutes", 60,
                        "scheduledAt", "2026-11-19T19:00:00",
                        "room", "Hoersaal 2",
                        "speakerId", 2))
                .when().put("/api/talks/" + id)
                .then()
                .statusCode(200)
                .body("durationMinutes", is(60))
                .body("room", is("Hoersaal 2"))
                .body("speaker.id", is(2));

        given().when().delete("/api/talks/" + id).then().statusCode(204);
        given().when().get("/api/talks/" + id).then().statusCode(404);
    }

    @Test
    void rejectsATalkForAnUnknownSpeaker() {
        given()
                .contentType("application/json")
                .body(Map.of(
                        "title", "Ghost Talk",
                        "summary", "Nobody gives this one.",
                        "durationMinutes", 30,
                        "scheduledAt", "2026-11-19T19:00:00",
                        "room", "Hoersaal 1",
                        "speakerId", 9999))
                .when().post("/api/talks")
                .then()
                .statusCode(400);
    }

    @Test
    void listsTheTalksOfASpeaker() {
        given()
                .when().get("/api/speakers/1/talks")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("speaker.id", everyItem(is(1)));
    }
}
