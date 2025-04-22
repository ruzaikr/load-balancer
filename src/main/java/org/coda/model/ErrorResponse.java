package org.coda.model;

public class ErrorResponse {

  private int status;
  private String error;
  private String details;

  public ErrorResponse() {}

  public ErrorResponse(int status, String error, String details) {
    this.status = status;
    this.error = error;
    this.details = details;
  }

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }

}
