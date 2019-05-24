package cn.fritt.sxc.proc;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.*;

import cn.fritt.sxc.ByteArraySxc;
import cn.fritt.sxc.Global;
import cn.fritt.sxc.SxcSocketClient;
import cn.fritt.sxc.data.DeviceMngData;
import cn.fritt.sxc.data.BoardMapInfoData;
import cn.fritt.sxc.data.DeviceBaseAttr;

public abstract class NetManagerProcess implements ManagerProcessor {

	public ByteArraySxc retBuff = null;
	public short _ver = 0x0301;
	public String _src = "B";
	public List<BoardMapInfoData> bList = new ArrayList<BoardMapInfoData>();
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

    public ByteArraySxc getReturnBuffer() {
    	return retBuff;
    }
    
    public void setFailedReturnBuff(int errResult, String errMsg){
    	retBuff = new ByteArraySxc(20000);
    	retBuff.setPtr(Global.NMS_PACKET_HEAD_LEN);
    	retBuff.appendShort(errResult);
    	retBuff.appendString(errMsg, 128);        
    }
    
    public ByteArraySxc getSendPacket(int packetType, ByteArraySxc buff) {
    	ByteArraySxc sendBuff = new ByteArraySxc(buff.getPtr() + 100);
    	sendBuff.setPtr(4);
    	int sid = Global.getSerialId();
    	sendBuff.appendInt(sid);
    	int ptype = 0x00000000;
    	sendBuff.appendInt(ptype);
    	int groups = 0x0001;
    	int gid = 0x0001;
    	sendBuff.appendShort(groups);
    	sendBuff.appendShort(gid);
    	int plen = buff.getPtr();
    	sendBuff.appendInt(plen + 2);
    	sendBuff.appendShort(packetType);
    	sendBuff.appendBytes(buff.getBuff(), 0, plen);
    	int crc = Global.getCrc32(sendBuff.getBuff(), 0, sendBuff.getPtr());
    	sendBuff.appendInt(crc);
    	int packLen = sendBuff.getPtr();
    	sendBuff.setIntAt(packLen - 4, 0);
   	    return sendBuff;
    }
    
   
    public ByteArraySxc getInfoFromDev(int packetType, ByteArraySxc buff, int devId) {
    	ByteArraySxc sendBuff = getSendPacket(packetType, buff);
    	DeviceBaseAttr ne = Global.getInst().getDevInfo(devId);
    	if (ne == null){
    		setFailedReturnBuff(-1001, "设备(ID=" + devId + ")不存在");
    		return null;
    	}
    	if (ne.getType() != 1){
    		setFailedReturnBuff(-1001, "设备(ID=" + devId + ")类型不匹配");
    		return null;
  		
    	}
    	
    	DeviceMngData nex = (DeviceMngData)ne;
    	SxcSocketClient sock = new SxcSocketClient(nex.getIp(), nex.getPort());
    	if (sock.getSock() == null){
    		setFailedReturnBuff(-1, "设备(ID=" + devId + ")网络故障");
    		sock.close();
    		return null;
    	}
    	System.out.println("connect to " + sock.getSock().getInetAddress().getHostAddress());
    	byte[] send = sendBuff.getByteBuffer();
//    	printDevCommand(send);
    	sock.sendPacket(send, sendBuff.getPtr());
    	byte[] head = new byte[4];
    	InputStream devIn = null;
    	try {
			devIn = sock.getSock().getInputStream();
		} catch (IOException e) {
			e.printStackTrace();
			setFailedReturnBuff(-1, "设备(ID=" + devId + ")网络故障");
    		sock.close();
    		return null;  
		}
    	int rlen = sock.recvPacket(devIn, head);
    	if (rlen < 0){
    		System.out.println("receive packet from " + nex.getIp() + " error");
    		setFailedReturnBuff(-1000, "设备(ID=" + devId + ")网络故障");
    		try {
				devIn.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
    		sock.close();
    		return null;
    	}
    	System.out.println("receive packet length is");
    	Global.printBytes(head, 0, 4);
    	if (rlen < 0){
    		setFailedReturnBuff(-1, "设备(ID=" + devId + ")网络故障");
    		try {
				devIn.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
   		    sock.close();
    		return null;    		
    	}
    	int pLen = ByteArraySxc.parseInt(head, 0);
    	byte[] rBuff = new byte[pLen];
    	rlen = 0;
    	int p0 = 0;
    	while(p0 < pLen - 4){
    		byte[] buff0 = new byte[4096];
    	    rlen = sock.recvPacket(devIn, buff0);
    	    if (rlen < 0) {
        		try {
    				devIn.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    	    	break;
    	    }
    	    for (int i = 0; i < rlen; i++){
    	    	rBuff[p0 + i] = buff0[i];
    	    }
    	    p0 = rlen + p0;
    	    try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    	}
		try {
			devIn.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    	
    	sock.close();
    	System.out.println("receive from device " + p0);
//    	Global.printBytes(rBuff, 0, p0);
    	printDevResponse(rBuff);
    	ByteArraySxc out = new ByteArraySxc(rBuff);
 //   	int pLen = out.parseInt();
    	int rcrc = out.getIntAt(pLen - 4);
    	int crc = Global.getCrc32(out.getBuff(), 0, pLen - 4);
    	if (rcrc != crc) {
//    		return null;
    	}
    	out.setPtr(Global.DEV_PACKET_HEAD_LEN - 4);    	
    	return out;
    }
    
    public void printDevHead(ByteArraySxc buff){
    	System.out.println("报文编号：0x" + Global.intToHexString(buff.parseInt(), 8));
    	System.out.println("报文类型：0x" + Global.intToHexString(buff.parseInt(), 8));
    	System.out.println("报文分组：0x" + Global.intToHexString(buff.parseInt(), 8));
    	System.out.println("正文长度：" + buff.parseInt());
    }
    
    public void printMngHead(ByteArraySxc buff){
    	System.out.println("同步标志：0x" + Global.intToHexString(buff.parseInt(), 8));
    	System.out.println("版本号：0x" + Global.intToHexString(buff.parseShort(), 4));
    	System.out.println("设备标识：" + buff.parseString(14).trim());
    	System.out.println("包长度：" + buff.parseInt());
    	System.out.println("包序号：0x" + Global.intToHexString(buff.parseInt(), 8));
    	System.out.println("扩展帧类型编码：0x" + Global.intToHexString(buff.parseShort(), 4));
    	System.out.println("帧类型编码：0x" + Global.intToHexString(buff.parseShort(), 4));
    	buff.parseBytes(32);
    }
    
    public abstract void printMngCommand(byte[] cmd);
    public abstract void printMngResponse(byte[] resp);
    public abstract void printDevCommand(byte[] cmd);
    public abstract void printDevResponse(byte[] resp);
    public int getBoardIdMngMapDev(int devId, int mngBoardId){
    	for (BoardMapInfoData d: bList){
    		if (d.devId == devId && d.mngBid == mngBoardId){
    			return d.devBid;
    		}
    	}
    	return mngBoardId;
    }
    
    public int getBoardIdDevMapMng(int devId, int devBoardId){
    	for (BoardMapInfoData d: bList){
    		if (d.devId == devId && d.devBid == devBoardId){
    			return d.mngBid;
    		}
    	}
    	return devBoardId;
    }

    public void initBoardMapInfo(int devId){
    	bList = BoardMapFileHandler.readMapFromFile(devId);
    }
    
    public byte[] errCodeConvert(int devId, byte[] errCode) {
    	byte[] ret = null;
		try {
			String s = "设备（" + devId + "）" + new String(errCode, "UTF-8");
			ret = s.getBytes("GBK");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	return ret;
    }
}
