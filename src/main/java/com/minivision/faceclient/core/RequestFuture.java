package com.minivision.faceclient.core;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.minivision.faceclient.ex.FaceException;
import com.minivision.faceclient.protocol.Packet;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @author PanXinmiao
 *
 * @param <T> Response的类别
 * 
 */
@Slf4j
public class RequestFuture<T> {
  
  private Packet<?> request;
  Class<T> responseBodyType;
  private Packet<T> response;
  private long buildNanoTime;
  private long sendNanoTime;
  private long responseNanoTime;
  private volatile boolean done;
  private Throwable throwable;
  
  private List<FutureListener<T>> listeners = Collections.synchronizedList(new ArrayList<>());
  
  private static ExecutorService listenerProcessors;
  
  static{
    
    /*核心线程数为cpu个数
            任务缓冲队列容量 = 线程池核心线程数+1 */
    listenerProcessors = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
        Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, 
        new LinkedBlockingQueue<>(Runtime.getRuntime().availableProcessors() + 1),  
        new ThreadFactory() {
          @Override
          public Thread newThread(Runnable r) {
            return new Thread(r, "Callback-Workers");
          }
        });  
        
  }

  public RequestFuture(Packet<?> request, Class<T> responseBodyType) {
    this.request = request;
    this.responseBodyType = responseBodyType;
    this.buildNanoTime = System.nanoTime();
  }
  

  public boolean isDone() {
    return done;
  }
  
  public int getSerialNum(){
    return request.getHead().getSerialNum();
  }

  public Packet<?> getRequest() {
    return request;
  }

  public void setRequest(Packet<?> request) {
    this.request = request;
  }

  public Packet<T> getResponse() throws FaceException {
    waitResponse();
    if (throwable != null) {
      log.error("Invalid response", throwable);
      throw new FaceException("Invalid response", throwable);
    }
    
    return response;
  }
  
  public T get() throws FaceException {
    return getResponse().getBody();
  }

  public Packet<T> getResponse(long timeout) throws FaceException {
    waitResponse(timeout);
    if (throwable != null) {
      log.error("Invalid response", throwable);
      throw new FaceException("Invalid response", throwable);
    }
    
    return response;
  }

  private void waitResponse(long timeout) {
    long startWait = System.currentTimeMillis();
    long limitTime = timeout;
    synchronized (this) {
      while (!done) {
        try {
          this.wait(limitTime);
          limitTime = timeout - System.currentTimeMillis() + startWait;
          if (limitTime <= 0) {
            this.responseNanoTime = System.nanoTime();
            this.throwable =
                new FaceException("Request timeout : " + timeout);
            this.done = true;
          }
        } catch (InterruptedException e) {
          log.error("Interrupted while waiting response.", e);
        }
      }
    }
  }

  private void waitResponse() {
    synchronized (this) {
      while (!done) {
        try {
          this.wait();
        } catch (InterruptedException e) {
          log.error("Interrupted while waiting response.", e);
        }
      }
    }
  }

  public void setResponse(Packet<T> response) {
    synchronized (this) {
      this.responseNanoTime = System.nanoTime();
      this.response = response;
      this.done = true;
      this.notifyAll();
    }
    
    doCallback();
  }

  public void fail(Throwable t) {
    synchronized (this) {
      this.responseNanoTime = System.nanoTime();
      this.throwable = t;
      this.done = true;
      this.notifyAll();
    }
    
    doCallback();
  }

  public long getBuildNanoTime() {
    return buildNanoTime;
  }

  public long getSendNanoTime() {
    return sendNanoTime;
  }

  public void setSendNanoTime(long sendNanoTime) {
    this.sendNanoTime = sendNanoTime;
  }

  public long getResponseNanoTime() {
    return responseNanoTime;
  }

  public Class<T> getResponseBodyType() {
    return responseBodyType;
  }

  public void setResponseBodyType(Class<T> responseBodyType) {
    this.responseBodyType = responseBodyType;
  }
  
  public void addListener(FutureListener<T> l){
    listeners.add(l);
  }
  
  public void cancelListener(FutureListener<T> l){
    listeners.remove(l);
  }
  
  private void doCallback(){
    for(FutureListener<T> l: listeners){
      listenerProcessors.submit(new Runnable() {
        @Override
        public void run() {
          if(throwable == null){
            l.onSucess(response.getBody());
          }else{
            l.onFail(throwable);
          }
        }
      });
      
      listenerProcessors.submit(new Runnable() {
        @Override
        public void run() {
          l.onComplete(RequestFuture.this);
        }
      });
    }
  }
  
  @Override
  public String toString() {
    return "RequestFuture [request=" + request + ", responseBodyType=" + responseBodyType
        + ", response=" + response + ", buildNanoTime=" + buildNanoTime + ", sendNanoTime="
        + sendNanoTime + ", responseNanoTime=" + responseNanoTime + ", done=" + done
        + ", throwable=" + throwable + "]";
  }

}
