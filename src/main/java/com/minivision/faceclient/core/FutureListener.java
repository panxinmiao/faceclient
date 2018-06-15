package com.minivision.faceclient.core;

public interface FutureListener<T> {

  public void onSucess(T result);
  
  public void onFail(Throwable t);
  
  public void onComplete(RequestFuture<T> future);
  
}
