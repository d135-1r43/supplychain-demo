package net.herhoffer;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.List;

@Path("/api/talks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Talks")
public class TalkResource {

    /**
     * Request payload for creating and updating talks. The speaker is referenced by id rather
     * than nested, so a talk can be moved to another speaker without touching the speaker.
     */
    public record TalkRequest(String title, String summary, int durationMinutes,
                              LocalDateTime scheduledAt, String room, Long speakerId) {
    }

    @GET
    public List<Talk> list(@QueryParam("room") String room) {
        return room == null ? Talk.bySchedule() : Talk.byRoom(room);
    }

    @GET
    @Path("/{id}")
    public Talk get(@PathParam("id") Long id) {
        return find(id);
    }

    @POST
    @Transactional
    public Response create(TalkRequest request) {
        Talk talk = new Talk();
        apply(request, talk);
        talk.persist();
        return Response.status(Response.Status.CREATED).entity(talk).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Talk update(@PathParam("id") Long id, TalkRequest request) {
        Talk talk = find(id);
        apply(request, talk);
        return talk;
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        find(id).delete();
        return Response.noContent().build();
    }

    private void apply(TalkRequest request, Talk talk) {
        Speaker speaker = Speaker.findById(request.speakerId());
        if (speaker == null) {
            throw new WebApplicationException("Speaker " + request.speakerId() + " not found",
                    Response.Status.BAD_REQUEST);
        }
        talk.title = request.title();
        talk.summary = request.summary();
        talk.durationMinutes = request.durationMinutes();
        talk.scheduledAt = request.scheduledAt();
        talk.room = request.room();
        talk.speaker = speaker;
    }

    private Talk find(Long id) {
        Talk talk = Talk.findById(id);
        if (talk == null) {
            throw new WebApplicationException("Talk " + id + " not found", Response.Status.NOT_FOUND);
        }
        return talk;
    }
}
