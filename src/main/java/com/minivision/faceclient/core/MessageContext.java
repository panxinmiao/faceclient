package com.minivision.faceclient.core;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.minivision.faceclient.Config;
import com.minivision.faceclient.ex.FaceException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageContext {

  private ConcurrentMap<Integer, RequestFuture<?>> reqCache = new ConcurrentHashMap<>();
  private long nanoTimeout;
  private ScheduledExecutorService timeoutChecker;

  // private Timer timer = new Timer("TimeoutChecker");

  private int responseTimeoutInSeconds = 10;
  private int maxConcurrent = 200;
  private int checkTimeoutPeriod = responseTimeoutInSeconds / 2;

  public MessageContext() {
    init();
  }

  public MessageContext(Config config) {
    this.maxConcurrent = config.getMaxConcurrent();
    this.responseTimeoutInSeconds = config.getResponseTimeout();
    this.checkTimeoutPeriod = config.getCheckTimeoutPeriod();
    init();
  }

  public MessageContext(int maxConcurrent) {
    this.maxConcurrent = maxConcurrent;
    init();
  }

  private void init() {
    nanoTimeout = TimeUnit.SECONDS.toNanos(responseTimeoutInSeconds);

    timeoutChecker = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
        return new Thread(r, "TimeoutChecker");
      }
    });


    timeoutChecker.scheduleWithFixedDelay(() -> {
      checkExpire();
    }, checkTimeoutPeriod, checkTimeoutPeriod, TimeUnit.SECONDS);


    /*
     * timer.schedule(new TimerTask() {
     * 
     * @Override public void run() { checkExpire(); } },
     * TimeUnit.SECONDS.toMillis(checkTimeoutPeriod),
     * TimeUnit.SECONDS.toMillis(checkTimeoutPeriod));
     */
  }

  /**
   * 定时任务检查容器中的超时请求
   */
  // @Scheduled(fixedRate = 5000) //TODO
  public void checkExpire() {
    long start = System.nanoTime();
    for (Entry<Integer, RequestFuture<?>> entry : reqCache.entrySet()) {
      RequestFuture<?> req = entry.getValue();
      long aliveTime = System.nanoTime() - req.getBuildNanoTime();
      if (req != null && aliveTime > nanoTimeout) {
        req = reqCache.remove(entry.getKey());
        // check again in case of anyone else removed it
        if (req != null) {
          req.fail(new FaceException("Request timeout"));
          log.warn("request {} timeout after {}ns", req.getRequest(), aliveTime);
        }
      }
    }
    long duration = System.nanoTime() - start;
    log.trace("TimeoutChecker finished in {}ns", duration);
  }

  public void add(RequestFuture<?> req) {
    if (maxConcurrent > 0 && reqCache.size() >= maxConcurrent) {
      req.fail(new FaceException(
          "Excessive number of concurrent requests , the limit is : " + maxConcurrent));
    }
    int id = req.getRequest().getHead().getSerialNum();
    if (reqCache.putIfAbsent(id, req) != null) {
      throw new IllegalArgumentException("Request with same serial number, "
          + req.getRequest().getHead().getSerialNum() + " already exists");
    }
  }

  public RequestFuture<?> remove(int id) {
    return reqCache.remove(id);
  }

}
