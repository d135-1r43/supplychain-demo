package net.herhoffer;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/speakers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Speakers")
public class SpeakerResource {

    @GET
    public List<Speaker> list(@QueryParam("company") String company) {
        return company == null ? Speaker.listAll() : Speaker.list("company", company);
    }

    @GET
    @Path("/{id}")
    public Speaker get(@PathParam("id") Long id) {
        return find(id);
    }

    @GET
    @Path("/{id}/talks")
    public List<Talk> talks(@PathParam("id") Long id) {
        find(id);
        return Talk.bySpeaker(id);
    }

    @POST
    @Transactional
    public Response create(Speaker speaker) {
        speaker.id = null;
        speaker.persist();
        return Response.status(Response.Status.CREATED).entity(speaker).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        find(id).delete();
        return Response.noContent().build();
    }

    static Speaker find(Long id) {
        Speaker speaker = Speaker.findById(id);
        if (speaker == null) {
            throw new WebApplicationException("Speaker " + id + " not found", Response.Status.NOT_FOUND);
        }
        return speaker;
    }
}
