package com.minivision.faceclient.protocol;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class BasePacketElement {
  
  public abstract byte[] encode();
  
  public abstract BasePacketElement decode(byte[] bytes);
  
  public abstract int getSize();
}

