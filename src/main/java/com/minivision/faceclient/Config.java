package com.minivision.faceclient;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Client的配置类
 * 
 * @author PanXinmiao
 *
 */
@Setter
@Getter
@ToString
public class Config {
  
  private String ip;
  private int port;
  private int maxConcurrent = 200;
  private int heartbeatTimeout = 10;
  private int heartbeatPeriod = 5;
  private int responseTimeout = 10;
  
  private int checkTimeoutPeriod = responseTimeout / 2;
  private int reconnectPeriod = 5;
}
