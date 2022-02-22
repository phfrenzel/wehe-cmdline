package mobi.meddle.wehe.bean;

import mobi.meddle.wehe.bean.Server;

public class ServerInstance {
  public Server server;
  public final String port;

  public ServerInstance(Server server, String port) {
    this.server = server;
    this.port = port;
  }
}
