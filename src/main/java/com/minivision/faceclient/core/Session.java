package com.minivision.faceclient.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.minivision.faceclient.Config;
import com.minivision.faceclient.protocol.Packet;
import com.minivision.faceclient.protocol.PacketUtil;
import com.minivision.faceclient.protocol.BasePacketElement;
import com.minivision.faceclient.protocol.Packet.Head;
import com.minivision.faceclient.protocol.Packet.Head.CmdCode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Session {

  private String serverIp;
  private int serverPort;
  private MessageContext messageContext;
  private Socket socket;

  private OutputStream os;
  private InputStream is;

  
  private Future<?> readTaskFuture;
  //private Thread readThread;
  
  // TODO需要吗？ 注意:现在是在方法调用的线程做IO写操作
  // private Thread writeThread;

  private volatile boolean connected;
  
  //private Timer heartbeart = new Timer("HeartBeart");
  
  private RequestFuture<Void> heartbeartFuture;
  
  private int heartbeatTimeout = 10;
  private int heartbeatPeriod = 5;
  private int reconnectPeriod = 5;
  
  
  private static ScheduledExecutorService heartbeartExecutor;
  private static ThreadPoolExecutor ioExecutor;
  
  static{
    heartbeartExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
      @Override
      public Thread newThread(Runnable r) {
        return new Thread(r, "HeartBeart");
      }
    });
    
    ioExecutor = new ThreadPoolExecutor(1, Integer.MAX_VALUE,
        60L, TimeUnit.SECONDS,
        new SynchronousQueue<Runnable>(), new ThreadFactory() {
          @Override
          public Thread newThread(Runnable r) {
            return new Thread(r, "IoExecutor");
          }
        });
  }
  
  public Session(String ip, int port){
    this.serverIp = ip;
    this.serverPort = port;
    reconnect();
  }
  
  public Session(Config config){
    this.serverIp = config.getIp();
    this.serverPort = config.getPort();
    this.heartbeatTimeout = config.getHeartbeatTimeout();
    this.heartbeatPeriod = config.getHeartbeatPeriod();
    this.reconnectPeriod = config.getReconnectPeriod();
    this.messageContext = new MessageContext(config);
    reconnect();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void init() throws UnknownHostException, IOException {
    connect();
    readTaskFuture = ioExecutor.submit(() -> {
      log.info("[{}] io read task started.", socket);
      while (connected) {
        try {
          Head head = readHead();
          //System.out.println("receive head :" + head);
          if(head.getCmd() == CmdCode.HEATRBEAT_ACK){
            if(heartbeartFuture!=null && heartbeartFuture.getSerialNum() == head.getSerialNum()){
              heartbeartFuture.setResponse(new Packet<Void>(head));
            }else{
              log.warn("Receive a unknown heartbeat ack [{}], maybe timeout, just discard it.");
            }
            
            continue;
          }
          
          int dataLen = head.getDataLen();
          byte[] dataBuffer = new byte[dataLen];
          readFull(is, dataBuffer, dataLen);
          
          RequestFuture<?> f = messageContext.remove(head.getSerialNum());
          
          if(f == null){
            log.warn("Receive a packet [sn={}], but not found in the cache, maybe timeout, just discard it", head.getSerialNum());
            continue;
          }
          
          Class<?> response = f.getResponseBodyType();
          BasePacketElement responseBody = PacketUtil.decode(dataBuffer, response);
          Packet reponse = new Packet<>(head, responseBody);
          f.setResponse(reponse);
          
        } catch (IOException e) {
          connected = false;
          e.printStackTrace();
        }
      }
      
      
    });
    
    heartbeartExecutor.scheduleWithFixedDelay(()->{
      try{
        heartbeart();
        heartbeartFuture.getResponse(TimeUnit.SECONDS.toMillis(heartbeatTimeout));
        //TODO heartbeat 健康检查
        long cost = heartbeartFuture.getResponseNanoTime() - heartbeartFuture.getBuildNanoTime();
        log.trace("complete a heartbeat, cost {} ms)", TimeUnit.NANOSECONDS.toMillis(cost));
        if(TimeUnit.NANOSECONDS.toSeconds(cost) > heartbeatTimeout/2){
          log.warn("one heartbeat cost too long time ({} ms)", TimeUnit.NANOSECONDS.toMillis(cost));
        }
      }catch(Exception e){
        e.printStackTrace();
        reconnect();
      }
    }, heartbeatPeriod, heartbeatPeriod, TimeUnit.SECONDS);
    
    /*readThread = new Thread(() -> {
      String name = Thread.currentThread().getName();
      log.info("{} started.", name);

      while (connected) {
        try {
          Head head = readHead();
          //System.out.println("receive head :" + head);
          if(head.getCmd() == CmdCode.HEATRBEAT_ACK){
            if(heartbeartFuture!=null && heartbeartFuture.getSerialNum() == head.getSerialNum()){
              heartbeartFuture.setResponse(new Packet<Void>(head));
            }else{
              log.warn("Receive a unknown heartbeat ack [{}], maybe timeout, just discard it.");
            }
            
            continue;
          }
          
          int dataLen = head.getDataLen();
          byte[] dataBuffer = new byte[dataLen];
          readFull(is, dataBuffer, dataLen);
          
          RequestFuture<?> f = messageContext.remove(head.getSerialNum());
          
          if(f == null){
            log.warn("Receive a packet [sn={}], but not found in the cache, maybe timeout, just discard it", head.getSerialNum());
            continue;
          }
          
          Class<?> response = f.getResponseBodyType();
          BasePacketElement responseBody = PacketUtil.decode(dataBuffer, response);
          Packet reponse = new Packet<>(head, responseBody);
          f.setResponse(reponse);
          
        } catch (IOException e) {
          connected = false;
          e.printStackTrace();
        }
      }
      
      
    }, socket + ": IO Read");

    readThread.start();*/
    
    /*heartbeart.schedule(new TimerTask() {
      @Override
      public void run() {
        try{
          heartbeart();
          heartbeartFuture.getResponse(TimeUnit.SECONDS.toMillis(heartbeatTimeout));
          //TODO heartbeat 健康检查
          long cost = heartbeartFuture.getResponseNanoTime() - heartbeartFuture.getBuildNanoTime();
          log.trace("complete a heartbeat, cost {} ms)", TimeUnit.NANOSECONDS.toMillis(cost));
          if(TimeUnit.NANOSECONDS.toSeconds(cost) > heartbeatTimeout/2){
            log.warn("one heartbeat cost too long time ({} ms)", TimeUnit.NANOSECONDS.toMillis(cost));
          }
        }catch(Exception e){
          e.printStackTrace();
          reconnect();
        }
      }
    }, TimeUnit.SECONDS.toMillis(heartbeatPeriod), TimeUnit.SECONDS.toMillis(heartbeatPeriod));*/
  }


  public void send(Packet<? extends BasePacketElement> p) throws IOException{
    byte[] bs = PacketUtil.encode(p);
    synchronized (os) {
      os.write(bs);
      os.flush();
    }
  }
  
  public void send(Packet<? extends BasePacketElement> p, RequestFuture<?> future) throws IOException{
    send(p);
    future.setSendNanoTime(System.nanoTime());
    messageContext.add(future);
  }
  
  private void heartbeart(){
    Head head = new Head(Packet.Head.CmdCode.HEATRBEAT);
    Packet<Void> heart = new Packet<>(head);
    heartbeartFuture = new RequestFuture<>(heart, Void.class);
    try {
      synchronized (os) {
        os.write(head.encode());
        os.flush();
      }
    } catch (IOException e) {
      e.printStackTrace();
      heartbeartFuture.fail(e);
    }
  }

  private Head readHead() throws IOException {
    byte[] headBuffer = new byte[12];
    readFull(is, headBuffer, 12);
    Head head = PacketUtil.decode(headBuffer, Head.class);
    return head;
  }

  private void readFull(InputStream is, byte[] buffer, int size) throws IOException {
    int read = 0;
    while (read < size) {
      read += is.read(buffer, read, size);
    }
  }


  public void connect() throws UnknownHostException, IOException {
    if (connected) {
      return;
    }
    socket = new Socket(serverIp, serverPort);
    os = new BufferedOutputStream(socket.getOutputStream());
    is = new BufferedInputStream(socket.getInputStream());
    connected = true;
  }

  public void disconnect(){
    if (!connected) {
      return;
    }
    
    connected = false;
    try {
      readTaskFuture.get(); //wait for read task completed
    } catch (InterruptedException | ExecutionException e1) {
      e1.printStackTrace();
    }
    /*if(readThread.isAlive()){
      try {
        readThread.join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }*/
    try {
      socket.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    
  }
  
  public void reconnect(){
    disconnect();
    
    while(!connected){
      try {
        init();
        log.info("connect success.");
      } catch (IOException e) {
        log.info("connect fail: [{}], try reconnect after {} seconds...", e.getMessage(), reconnectPeriod);
        try {
          Thread.sleep(TimeUnit.SECONDS.toMillis(reconnectPeriod));
        } catch (InterruptedException e1) {
          e1.printStackTrace();
        }
      }
    }
    
  }

  public MessageContext getMessageContext() {
    return messageContext;
  }

  public void setMessageContext(MessageContext messageContext) {
    this.messageContext = messageContext;
  }


}
