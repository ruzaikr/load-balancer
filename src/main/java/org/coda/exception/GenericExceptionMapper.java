package org.coda.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.coda.model.ErrorResponse;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {

  @Override
  public Response toResponse(Throwable exception) {

    int statusCode = exception instanceof WebApplicationException
        ? ((WebApplicationException) exception).getResponse().getStatus()
        : Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();

    ErrorResponse errorResponse = new ErrorResponse(
        statusCode,
        exception.getClass().getSimpleName(),
        exception.getMessage()
    );

    return Response.status(statusCode)
        .entity(errorResponse)
        .build();
  }
}
