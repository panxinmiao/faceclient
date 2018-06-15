package com.minivision.faceclient.ex;

public class FaceException extends RuntimeException {

  public FaceException(String message) {
    super(message);
  }
  
  public FaceException(String message, Throwable cause) {
    super(message, cause);
  }
}
