package com.minivision.faceclient;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.slf4j.LoggerFactory;

import com.minivision.faceclient.core.FutureListenerAdapter;
import com.minivision.faceclient.core.RequestFuture;
import com.minivision.faceclient.protocol.Packet;
import com.minivision.faceclient.protocol.Packet.FaceFeatures;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

public class ClientTest {
  public static void main(String[] args) throws IOException {
    
    LoggerContext loggerContext= (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger logger=loggerContext.getLogger("root");
    logger.setLevel(Level.toLevel("TRACE"));
    
    Client client = new Client("192.168.123.200", 9999);
    
    byte[] bs = FileUtils.readFileToByteArray(new File("E://14_270.jpg"));
    
    for(int i=0; i<10; i++){
      long start = System.currentTimeMillis();
      RequestFuture<FaceFeatures> future = client.getFeatures(bs, true, true, true);
      
      future.addListener(new FutureListenerAdapter<Packet.FaceFeatures>() {
        
        @Override
        public void onSucess(FaceFeatures result) {
          System.out.println("onSucess");
          System.out.println(result);
        }
        
        @Override
        public void onComplete(RequestFuture<FaceFeatures> f) {
          System.out.println("onComplete");
          System.out.println(f.getBuildNanoTime());
          System.out.println(f.getSendNanoTime());
          System.out.println(f.getResponseNanoTime());
        }
      });
      
      
      FaceFeatures ff = future.get();
      
      long end = System.currentTimeMillis();
      
      System.out.println("同步");
      System.out.println(ff);
      
      System.out.println("cost: " + (end-start)+"ms");
    }

    
  }
}
