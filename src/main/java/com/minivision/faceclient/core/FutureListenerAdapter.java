package com.minivision.faceclient.core;

public class FutureListenerAdapter<T> implements FutureListener<T>{
  @Override
  public void onSucess(T result) {
    
  }

  @Override
  public void onFail(Throwable t) {
    
  }

  @Override
  public void onComplete(RequestFuture<T> future) {
    
  }
}