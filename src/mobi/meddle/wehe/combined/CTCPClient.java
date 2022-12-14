package mobi.meddle.wehe.combined;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import mobi.meddle.wehe.util.Log;
import mobi.meddle.wehe.bean.Server;

/**
 * A TCP connection to the server for a client-server pair. (Currently each replay has one
 * client-server pair, so this can be seen as a TCP connection for a replay.)
 */
public class CTCPClient {
  final String CSPair;
  private final String destIP;
  private final int destPort;
  final String replayName;
  final Server publicIP;
  Socket socket = null; //socket to connect to server

  /**
   * Constructor.
   *
   * @param cSPair     the client-server pair
   * @param destIP     server IP address
   * @param destPort   server port to connect to
   * @param replayName name of the replay
   * @param publicIP   IP address of the user's device
   */
  public CTCPClient(String cSPair, Server destIP, int destPort,
                    String replayName, Server publicIP) {
    this.CSPair = cSPair;

    String serverAddr = cSPair.split("-")[1];

    if (serverAddr.contains(":")) {
      this.destIP = destIP.getIpv6();
    } else {
      this.destIP = destIP.getIpv4();
    }

    this.destPort = destPort;
    this.replayName = replayName;
    this.publicIP = publicIP;
  }

  /**
   * Steps: 1- Create and connect TCP socket 2- Identifies itself --> tells server what's replaying
   * (replay_name and c_s_pair)
   */
  void createSocket() {
    try {
      socket = new Socket();
      InetSocketAddress endPoint = new InetSocketAddress(destIP, destPort);
      socket.setTcpNoDelay(true);
      socket.setReuseAddress(true);
      socket.setKeepAlive(true);
      socket.connect(endPoint);
    } catch (Exception e) {
      Log.e("Client", "Error creating TCP socket: " + destIP, e);
    }
  }

  /**
   * Close the connection to the server.
   */
  public void close() {
    try {
      this.socket.close();
      Log.i("Client", "Socket is now closed");
    } catch (IOException e) {
      Log.w("Client", "Problem closing TCP socket", e);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }

    if (!(o instanceof CTCPClient)) {
      return false;
    }

    return this.CSPair.equals(((CTCPClient) o).CSPair);
  }

  @Override
  public int hashCode() {
    return CSPair.hashCode();
  }
}
