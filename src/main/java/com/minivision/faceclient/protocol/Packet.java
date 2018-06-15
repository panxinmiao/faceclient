package com.minivision.faceclient.protocol;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class Packet<T> {

  private Head head;
  private T body;

  public Packet() {}

  public Packet(Head head) {
    setHead(head);
  }

  public Packet(Head head, T body) {
    setHead(head);
    setBody(body);
  }

  @Setter
  @Getter
  @ToString
  public static class Head extends BasePacketElement {
    private byte version;
    private int serialNum;
    private short cmd;
    private byte status;
    private int dataLen;

    public Head() {
      version = 1;
      serialNum = PacketUtil.getNextId();
    }

    public Head(short cmd) {
      this();
      setCmd(cmd);
    }

    public static class CmdCode {
      public static final short HEATRBEAT = 0;
      public static final short HEATRBEAT_ACK = 1;
      public static final short GET_FEATURE = 10;
      public static final short GET_FEATURE_ACK = 11;
    }

    @Override
    public byte[] encode() {
      ByteBuffer buffer = ByteBuffer.allocate(12);
      buffer.put(version);
      buffer.putInt(serialNum);
      buffer.putShort(cmd);
      buffer.put(status);
      buffer.putInt(dataLen);
      return buffer.array();
    }

    @Override
    public Head decode(byte[] bytes) {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      version = buffer.get();
      serialNum = buffer.getInt();
      cmd = buffer.getShort();
      status = buffer.get();
      dataLen = buffer.getInt();
      return this;
    }

    @Override
    public int getSize() {
      return 12;
    }
  }

  @Setter
  @Getter
  @ToString
  public static class ImageData extends BasePacketElement {
    private boolean useAge;
    private boolean useGender;
    private boolean useFeature = true;
    private int imgSize;
    private byte[] imgData;

    @Override
    public byte[] encode() {
      assert (imgSize == imgData.length);
      ByteBuffer buffer = ByteBuffer.allocate(7 + imgSize);
      buffer.put(useAge ? (byte) 1 : (byte) 0);
      buffer.put(useGender ? (byte) 1 : (byte) 0);
      buffer.put(useFeature ? (byte) 1 : (byte) 0);
      buffer.putInt(imgSize);
      buffer.put(imgData);
      return buffer.array();
    }

    @Override
    public BasePacketElement decode(byte[] bytes) {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      useAge = buffer.get() != 0;
      useGender = buffer.get() != 0;
      useFeature = buffer.get() != 0;
      imgSize = buffer.getInt();
      imgData = new byte[imgSize];
      buffer.get(imgData);
      return this;
    }

    @Override
    public int getSize() {
      return 7 + imgData.length;
    }

  }

  @ToString
  public static class FaceFeatures extends BasePacketElement {
    private short faceNum;
    private List<FaceFeature> features;

    @Override
    public byte[] encode() {
      ByteBuffer buffer = null;
      if(faceNum==0 || features == null){
        buffer = ByteBuffer.allocate(2);
        buffer.putShort(faceNum);
        return buffer.array();
      }
      assert (faceNum == features.size());
      
      for (FaceFeature f : features) {
        byte[] bs = f.encode();
        if (buffer == null) {
          buffer = ByteBuffer.allocate(2 + faceNum * bs.length);
          buffer.putShort(faceNum);
        }
        buffer.put(bs);
      }
      return buffer.array();
    }

    @Override
    public BasePacketElement decode(byte[] bytes) {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      faceNum = buffer.getShort();
      if(faceNum != 0){
        features = new ArrayList<FaceFeature>();
        int fsize = buffer.remaining() / faceNum;
        //System.out.println("fsize : "+ fsize);
        for(int i=0; i<faceNum; i++){
          byte[] fbs = new byte[fsize];
          buffer.get(fbs);
          FaceFeature f = new FaceFeature();
          f.decode(fbs);
          features.add(f);
        }
      }
      //System.out.println("size : "+ size);
      return this;
    }

    @Override
    public int getSize() {
      if(faceNum==0 || features == null){
        return 2;
      }
      int size =2;
      for(FaceFeature f : features){
        size += f.getSize();
      }
      return size;
    }

  }

  @Setter
  @Getter
  @ToString
  public static class FaceFeature extends BasePacketElement {
    private short faceRectLeft;
    private short faceRectTop;
    private short faceRectWidth;
    private short faceRectHeight;
    private short featureLen;
    private short age;
    private byte gender;
    private short facepicRotate;
    private float confidenceAge;
    private float confidenceGender;
    private float feature[];

    @Override
    public byte[] encode() {
      assert (featureLen == feature.length);
      ByteBuffer buffer = ByteBuffer.allocate(23 + 4 * featureLen);
      buffer.putShort(faceRectLeft);
      buffer.putShort(faceRectTop);
      buffer.putShort(faceRectWidth);
      buffer.putShort(faceRectHeight);
      buffer.putShort(featureLen);
      buffer.putShort(age);
      buffer.put(gender);
      buffer.putShort(facepicRotate);
      buffer.putFloat(confidenceAge);
      buffer.putFloat(confidenceGender);
      
      if(feature !=null){
        for (float f : feature) {
          buffer.putFloat(f);
        }
      }
      return buffer.array();
    }

    @Override
    public BasePacketElement decode(byte[] bytes) {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      faceRectLeft = buffer.getShort();
      faceRectTop = buffer.getShort();
      faceRectWidth = buffer.getShort();
      faceRectHeight = buffer.getShort();
      featureLen = buffer.getShort();
      age = buffer.getShort();
      gender = buffer.get();
      facepicRotate = buffer.getShort();
      confidenceAge = buffer.getFloat();
      confidenceGender = buffer.getFloat();
      feature = new float[featureLen];
      for (int i = 0; i < featureLen; i++) {
        feature[i] = buffer.getFloat();
      }
      return this;
    }

    @Override
    public int getSize() {
      return 23 + 4 * featureLen;
    }

  }

}

