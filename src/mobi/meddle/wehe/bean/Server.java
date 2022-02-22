package mobi.meddle.wehe.bean;

import java.util.Arrays;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import mobi.meddle.wehe.util.Log;

public class Server {
  private String hostname;
  private String ipv4;
  private String ipv6;

  public Server(String hostname) {
    this.hostname = hostname;
    this.ipv4 = "";
    this.ipv6 = "";

    if (!hostname.isEmpty()) {
      getServerIPs();
    }
  }

  public Server(String ipv4, String ipv6) {
    this.ipv4 = ipv4;
    this.ipv6 = ipv6;
  }

  public boolean hasIp() {
    return hasIpv4() || hasIpv6();
  }

  public boolean hasIpv4() {
    if (ipv4 == null || ipv4.isEmpty()) {
      return false;
    }

    return true;
  }

  public boolean hasIpv6() {
    if (ipv6 == null || ipv6.isEmpty()) {
      return false;
    }

    return true;
  }

  public String getIpv4() {
    return ipv4;
  }

  public String getIpv6() {
    return ipv6;
  }

  public void setIpv4(String ipv4) {
    this.ipv4 = ipv4;
  }

  public void setIpv6(String ipv6) {
    this.ipv6 = ipv6;
  }

  private void getServerIPs() {
    for (int i = 0; i < 5; i++) {
      try {
        InetAddress[] addrs = InetAddress.getAllByName(hostname);

        for (InetAddress addr : addrs) {
          if (addr instanceof Inet4Address) {
            this.ipv4 = addr.getHostAddress();
          }
          if (addr instanceof Inet6Address) {
            this.ipv6 = addr.getHostAddress();
          }
          if (!this.ipv4.equals("") && !this.ipv6.equals("")) {
            break;
          }
        }

        return;
      } catch (UnknownHostException e) {
        if (i == 4) {
          Log.e("getServerIP", "Failed to get IP of server", e);
        } else {
          Log.w("getServerIP", "Failed to get IP of server, trying again");
        }
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ex) {
          Log.w("getServerIP", "Sleep interrupted", ex);
        }
      }
    }
    return;
  }

  @Override
  public String toString() {
    return "Server [hostname=" + (hostname == null ? "(null)" : hostname) +
      ", ipv4=" + (ipv4 == null ? "(null)" : ipv4) +
      ", ipv6=" + (ipv6 == null ? "(null)" : ipv6) + "]";
  }
}
