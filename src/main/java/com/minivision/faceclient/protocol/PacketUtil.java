package com.minivision.faceclient.protocol;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;

import com.minivision.faceclient.ex.FaceException;

public class PacketUtil {
  
  @SuppressWarnings("unchecked")
  public static <T extends BasePacketElement> T decode(byte[] buffer, Class<?> c) throws FaceException{
    if(c == Void.class){
      return null;
    }
    
    try {
      Method method = c.getMethod("decode", byte[].class);
      T object = (T) method.invoke(c.newInstance(), buffer);
      return (T) object;
    } catch (Exception e) {
      e.printStackTrace();
      throw new FaceException("decode error.", e);
    }
  }
  
  
  public static byte[] encode(Packet<? extends BasePacketElement> packet){
    byte[] head = packet.getHead().encode();
    byte[] body = new byte[0];
    if(packet.getBody() != null){
      body = packet.getBody().encode();
    }
    ByteBuffer buffer = ByteBuffer.allocate(head.length + body.length);
    buffer.put(head);
    buffer.put(body);
    return buffer.array();
  }
  
  public static byte[] encode(BasePacketElement s){
    byte[] bs = s.encode();
    return bs;
  }
  
  private static SecureRandom r = new SecureRandom();
  private static AtomicInteger seq = new AtomicInteger(Math.abs(r.nextInt() / 10));

  public static int getNextId() {
    int id = seq.incrementAndGet();
    if (id < 0 || id == Integer.MAX_VALUE) {
      seq.compareAndSet(id, Math.abs(r.nextInt() / 10));
    }
    return id;
  }
}
