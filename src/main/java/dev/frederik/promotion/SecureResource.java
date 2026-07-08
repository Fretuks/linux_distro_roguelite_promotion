package dev.frederik.promotion;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/secure")
@Produces(MediaType.APPLICATION_JSON)
public class SecureResource {
    @GET
    @Path("/data")
    @RolesAllowed("Admin")
    public Response getSecureData() {
        return Response.ok(Map.of("message", "This is protected data.")).build();
    }
}
