package mobi.meddle.wehe.bean;

import java.util.ArrayList;

/**
 * Contains information about a replay (assets directory). Each replay JSON file contains the below
 * 4 fields.
 */
public class CombinedAppJSONInfoBean {
  private ArrayList<RequestSet> Q; //list of packets to send
  private ArrayList<String> udpClientPorts; //list of client ports for UDP
  private ArrayList<String> tcpCSPs; //list of client-server pairs for TCP
  private String replayName;
  private int udpCSPnum;

  public CombinedAppJSONInfoBean() {
    Q = new ArrayList<>();
    udpClientPorts = new ArrayList<>();
    tcpCSPs = new ArrayList<>();
    replayName = null;
    udpCSPnum = 0;
  }

  public ArrayList<RequestSet> getQ() {
    return Q;
  }

  public void setQ(ArrayList<RequestSet> q) {
    Q = q;
  }

  public ArrayList<String> getTcpCSPs() {
    return tcpCSPs;
  }

  public void setTcpCSPs(ArrayList<String> tcpCSPs) {
    this.tcpCSPs = tcpCSPs;
  }

  public ArrayList<String> getUdpClientPorts() {
    return udpClientPorts;
  }

  public void setUdpClientPorts(ArrayList<String> udpClientPorts) {
    this.udpClientPorts = udpClientPorts;
  }

  public String getReplayName() {
    return replayName;
  }

  public void setReplayName(String replayName) {
    this.replayName = replayName;
  }

  public void setUdpCSPnum(int num) {
      this.udpCSPnum = num;
  }

  public int getUdpCSPnum() {
      return udpCSPnum;
  }

  public boolean isTCP() {
    return !tcpCSPs.isEmpty();
  }
}
