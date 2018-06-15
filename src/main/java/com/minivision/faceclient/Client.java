package com.minivision.faceclient;

import java.io.IOException;

import com.minivision.faceclient.core.RequestFuture;
import com.minivision.faceclient.core.Session;
import com.minivision.faceclient.protocol.Packet;
import com.minivision.faceclient.protocol.Packet.FaceFeatures;
import com.minivision.faceclient.protocol.Packet.Head;
import com.minivision.faceclient.protocol.Packet.ImageData;
/**
 * 
 * 人脸算法服务客户端
 * @author PanXinmiao
 * 
 */
public class Client {
  
  private String ip;
  private int port;
  private Session session;
  
  public Client(String ip, int port){
    Config defaultConfig = new Config();
    defaultConfig.setIp(ip);
    defaultConfig.setPort(port);
    this.session = new Session(defaultConfig);
  }
  
  public Client(Config config){
    this.session = new Session(config);
  }
  
  public RequestFuture<FaceFeatures> getFeatures(byte[] img, boolean useFeature, boolean useAge, boolean useGender){
    Head head = new Head(Packet.Head.CmdCode.GET_FEATURE);
    
    ImageData data = new ImageData();
    data.setUseFeature(useFeature);
    data.setUseAge(useAge);
    data.setUseGender(useGender);
    data.setImgSize(img.length);
    data.setImgData(img);
    head.setDataLen(data.getSize());
    Packet<ImageData> request = new Packet<ImageData>(head, data);
    
    RequestFuture<FaceFeatures> future = new RequestFuture<>(request, FaceFeatures.class);
    
    try {
      session.send(request, future);
    } catch (IOException e) {
      e.printStackTrace();
      future.fail(e);
    }
    return future;
  }

  public String getIp() {
    return ip;
  }

  public void setIp(String ip) {
    this.ip = ip;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

}
