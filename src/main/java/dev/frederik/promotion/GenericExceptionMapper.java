package dev.frederik.promotion;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {
    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof NotFoundException) {
            return Response.status(404).entity(Map.of("error", "Route not found.")).build();
        }
        if (exception instanceof ClientErrorException clientErrorException) {
            return Response.status(clientErrorException.getResponse().getStatus())
                    .entity(Map.of("error", clientErrorException.getMessage()))
                    .build();
        }
        if (exception instanceof JsonProcessingException) {
            return Response.status(400).entity(Map.of("error", "Invalid JSON body.")).build();
        }
        return Response.status(500).entity(Map.of("error", "Internal server error.")).build();
    }
}
