package mobi.meddle.wehe;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import mobi.meddle.wehe.bean.ApplicationBean;
import mobi.meddle.wehe.bean.CombinedAppJSONInfoBean;
import mobi.meddle.wehe.bean.JitterBean;
import mobi.meddle.wehe.bean.RequestSet;
import mobi.meddle.wehe.bean.ServerInstance;
import mobi.meddle.wehe.bean.Server;
import mobi.meddle.wehe.bean.Server;
import mobi.meddle.wehe.bean.UDPReplayInfoBean;
import mobi.meddle.wehe.combined.CTCPClient;
import mobi.meddle.wehe.combined.CUDPClient;
import mobi.meddle.wehe.combined.CombinedAnalyzerTask;
import mobi.meddle.wehe.combined.CombinedNotifierThread;
import mobi.meddle.wehe.combined.CombinedQueue;
import mobi.meddle.wehe.combined.CombinedReceiverThread;
import mobi.meddle.wehe.combined.CombinedSideChannel;
import mobi.meddle.wehe.constant.Consts;
import mobi.meddle.wehe.constant.S;
import mobi.meddle.wehe.util.Config;
import mobi.meddle.wehe.util.Log;
import mobi.meddle.wehe.util.UtilsManager;

/**
 * Runs the replays.
 */
public class Replay {
  private final boolean runPortTests;
  private final Server serverDisplay; //server to display in the results
  //---------------------------------------------------
  private CombinedAppJSONInfoBean appData;
  private ApplicationBean app;
  private final ArrayList<Server> servers = new ArrayList<>(); //server to run the replays to
  private String metadataServer;
  private boolean doTest; //add a tail for testing data if true
  private final ArrayList<String> analyzerServerUrls = new ArrayList<>();
  //true if confirmation replay should run if the first replay has differentiation
  private final boolean confirmationReplays;
  private final boolean useDefaultThresholds;
  private int a_threshold;
  private final int ks2pvalue_threshold;
  private SSLSocketFactory sslSocketFactory = null;
  private HostnameVerifier hostnameVerifier = null;
  private boolean rerun = false; //true if confirmation replay
  //randomID, historyCount, and testId identifies the user, test number, and replay number
  //server uses these to determine which results to send back to client
  private String randomID; //unique user ID for certain device
  //historyCount is the test number; current number can be seen as number of apps run
  //or number of times user hit the run button for ports
  private int historyCount;
  //testId is replay number in a test
  //for apps - 0 is original replay, 1 is random replay
  //for ports - 0 non-443 port, 1 is port 443
  private int testId;
  //---------------------------------------------------
  private String appName; //app/port to run
  private final ArrayList<Timer> timers = new ArrayList<>();
  private final ArrayList<Integer> numMLab = new ArrayList<>(); //number of tries before successful MLab connection

  public Replay() {
    this.appName = Config.appName;
    this.runPortTests = appName.startsWith("port");
    this.serverDisplay = Config.serverDisplay;
    this.confirmationReplays = Config.confirmationReplays;
    this.useDefaultThresholds = Config.useDefaultThresholds;
    this.a_threshold = Config.a_threshold;
    this.ks2pvalue_threshold = Config.ks2pvalue_threshold;
  }

  /**
   * This method begins process to run tests. Step 1: Initialize several variables. Step 2: Run
   * tests. Step 3: Save results.
   *
   * @return exit status
   */
  public int beginTest() {
    /*
     * Step 1: Initialize several variables.
     */
    String[] info;
    try {
      info = Log.readInfo().split(";");
    } catch (IOException e) {
      Log.e("InfoFile", "Info file read error", e);
      Log.ui("ERR_INFO_RD", S.ERROR_IO_INFO);
      return Consts.ERR_INFO_RD;
    }
    historyCount = Integer.parseInt(info[1]);
    // update historyCount
    historyCount++;

    randomID = info[0];
    // This random ID is used to map the test results to a specific instance of app
    // It is generated only once and saved thereafter
    if (randomID == null) {
      Log.e("RecordReplay", "randomID does not exist!");
      Log.ui("ERR_NO_ID", S.ERROR_NO_USER_ID);
      return Consts.ERR_NO_ID;
    }

    //write randomID and updated historyCount to the info file
    Log.incHistoryCount();
    try {
      Log.writeInfo();
    } catch (IOException e) {
      Log.e("InfoFile", "Info file write error", e);
      Log.ui("ERR_INFO_WR", S.ERROR_IO_INFO);
      return Consts.ERR_INFO_WR;
    }
    Log.d("Replay", "historyCount: " + historyCount);

    app = parseAppJSON();
    if (app == null) {
      cleanUp();
      Log.wtf("noSuchApp", "Either there is no app named \"" + appName + "\", or a file"
              + " could not be found.");
      Log.ui("ERR_NO_TEST", S.ERROR_UNKNOWN_TEST);
      return Consts.ERR_NO_TEST;
    }

    //write current historyCount to applicationBean
    app.setHistoryCount(historyCount);

    // metadata here is user's network type device used geolocation if permitted etc
    metadataServer = Consts.METADATA_SERVER;
    int suc = setupServersAndCertificates(serverDisplay, metadataServer);
    if (suc != 0) {
      cleanUp();
      return suc;
    }

    testId = -1;
    doTest = false;

    //timing allows replays to be run with the same timing as when they were recorded
    //for example, if a YouTube video was paused for 2 seconds during recording, then the
    //replay will also pause for 2 seconds at that point in the replay
    //port tests try to run as fast as possible, so there is no timing for them
    Config.timing = !runPortTests;
    Server publicIP = getPublicIP("443"); //get user's IP address
    Config.publicIP = publicIP;
    Log.d("Replay", "public IP: " + publicIP);
    //If cannot connect to server, display an error and stop tests
    if (!publicIP.hasIp()) {
      cleanUp();
      Log.ui("ERR_CONN_IP", S.ERROR_NO_CONNECTION);
      return Consts.ERR_CONN_IP;
    }

    /*
     * Step 2: Run tests.
     */
    int exitCode = runTest(); // Run the test on this.app
    cleanUp();

    Log.ui("Finished", S.REPLAY_FINISHED_TITLE);
    if (exitCode == 0) {
      Log.i("Result Channel", "Exiting normally");
    }
    return exitCode;
  }

  /**
   * Close connections and cancel timers before exiting.
   */
  private void cleanUp() {
    for (Timer t : timers) {
      t.cancel();
    }
  }

  /**
   * This method parses apps_list.json file located in res folder. This file has all the basic
   * details of apps for replay.
   */
  private ApplicationBean parseAppJSON() {
    try {
      //get apps_list.json
      StringBuilder buf = new StringBuilder();
      File appInfo = new File(Config.APPS_FILENAME);
      Path tests_info_file = Paths.get(Config.APPS_FILENAME);
      if (!Files.exists(tests_info_file)) {
        Log.e("Load test", "\"" + Config.APPS_FILENAME + "\" file not found.");
        return null;
      }

      //read the apps/ports in the file
      Scanner scanner = new Scanner(appInfo);
      while (scanner.hasNextLine()) {
        buf.append(scanner.nextLine());
      }

      JSONObject jObject = new JSONObject(buf.toString());
      JSONArray jArray = jObject.getJSONArray("apps");
      String port443SmallFile = Config.RESOURCES_ROOT + jObject.getString("port443small");
      String port443LargeFile = Config.RESOURCES_ROOT + jObject.getString("port443large");

      String portType = "SMALL_PORT";
      if (runPortTests) {
        if (appName.toLowerCase().strip().endsWith("l")) {
          portType = "LARGE_PORT";
        }
        appName = appName.substring(0, appName.length() - 1);
      }

      JSONObject appObj;
      ApplicationBean bean;
      //load each app/port into ApplicationBeans
      for (int i = 0; i < jArray.length(); i++) {
        appObj = jArray.getJSONObject(i);

        if (appObj.getString("image").equals(appName)) { //have user enter image name into cmdline
          if (runPortTests && !appObj.getString("category").equals(portType)) {
            continue;
          }
          bean = new ApplicationBean();
          bean.setDataFile(Config.RESOURCES_ROOT + appObj.getString("datafile"));
          bean.setTime(appObj.getInt("time") * 2); //JSON time only for 1 replay
          bean.setImage(appObj.getString("image"));

          String cat = appObj.getString("category");
          //"random" test for ports is port 443
          if (cat.equals("SMALL_PORT")) {
            bean.setRandomDataFile(port443SmallFile);
          } else if (cat.equals("LARGE_PORT")) {
            bean.setRandomDataFile(port443LargeFile);
          } else {
            bean.setRandomDataFile(Config.RESOURCES_ROOT + appObj.getString("randomdatafile"));
          }

          if (cat.equals("SMALL_PORT") || cat.equals("LARGE_PORT")) {
            bean.setName(String.format(S.PORT_NAME, appObj.getString(("name"))));
          } else {
            bean.setName(appObj.getString("name")); //app names stored in JSON file
          }

          Path normal_test = Paths.get(bean.getDataFile());
          if (!Files.exists(normal_test)) {
            Log.e("Load test", bean.getName() + " normal test file \"" + bean.getDataFile()
                    + "\" not found.");
            return null;
          }
          Path rand_test = Paths.get(bean.getRandomDataFile());
          if (!Files.exists(rand_test)) {
            Log.e("Load test", bean.getName() + " random test file \"" + bean.getRandomDataFile()
                    + "\" not found.");
            return null;
          }
          return bean;
        }
      }
    } catch (IOException e) {
      Log.e(Consts.LOG_APP_NAME, "IOException while reading file " + Config.APPS_FILENAME, e);
    } catch (JSONException e) {
      Log.e(Consts.LOG_APP_NAME, "JSONException while parsing file " + Config.APPS_FILENAME, e);
    }
    return null;
  }

  /**
   * Gets IPs of server and metadata server. Connects to MLab authentication WebSocket if necessary.
   * Gets necessary certificates for server and metadata server.
   *
   * @param server         the hostname of the server to connect to
   * @param metadataServer the host name of the metadata server to connect to
   * @return 0 if everything properly sets up; error code otherwise
   */
  private int setupServersAndCertificates(Server server, String metadataServer) {
    // We first resolve the IP of the server and then communicate with the server
    // Using IP only, because we have multiple server under same domain and we want
    // the client not to switch server during a test run
    //wehe4.meddle.mobi 90% returns 10.0.0.0 (use MLab), 10% legit IP (is Amazon)

    servers.add(server);
    if (!servers.get(servers.size() - 1).hasIp()) {
      Log.ui("ERR_UNK_HOST", S.ERROR_UNKNOWN_HOST);
      return Consts.ERR_UNK_HOST;
    }
    // A hacky way to check server IP version
    boolean serverIPisV6 = false;
    if (servers.get(0).hasIpv6()) {
      serverIPisV6 = true;
    }
    Log.d("ServerIPVersion", servers.get(0) + (serverIPisV6 ? "IPV6" : "IPV4"));

    for (Server srvr : servers) {
      if (!srvr.hasIp()) { //check to make sure IP was returned by getServerIP
        Log.ui("ERR_UNK_HOST", S.ERROR_UNKNOWN_HOST);
        return Consts.ERR_UNK_HOST;
      }
    }
    Log.d("GetReplayServerIP", "Server IPs: " + servers);
    if (!generateServerCertificate(true)) {
      return Consts.ERR_CERT;
    }

    if (metadataServer != null) {
      this.metadataServer = metadataServer;
      if (this.metadataServer.equals("")) { //get IP and certificates for metadata server
        Log.ui("ERR_UNK_META_HOST", S.ERROR_UNKNOWN_META_HOST);
        return Consts.ERR_UNK_META_HOST;
      }
      if (!generateServerCertificate(false)) {
        return Consts.ERR_CERT;
      }
    }
    return Consts.SUCCESS;
  }

  /**
   * Gets the certificates for the servers
   *
   * @param main true if main server; false if metadata server
   * @return true if certificates successfully generated; false otherwise
   */
  private boolean generateServerCertificate(boolean main) {
    try {
      String server = main ? "main" : "metadata";
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      Certificate ca;
      InputStream caInput = new FileInputStream(main ? Config.MAIN_CERT : Config.META_CERT);
      ca = cf.generateCertificate(caInput);
      Log.d("Certificate", server + "=" + ((X509Certificate) ca).getIssuerDN());

      // Create a KeyStore containing our trusted CAs
      String keyStoreType = KeyStore.getDefaultType();
      KeyStore keyStore = KeyStore.getInstance(keyStoreType);
      keyStore.load(null, null);
      keyStore.setCertificateEntry(server, ca);

      // Create a TrustManager that trusts the CAs in our KeyStore
      String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
      TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
      tmf.init(keyStore);

      // Create an SSLContext that uses our TrustManager
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, tmf.getTrustManagers(), null);
      if (main) {
        sslSocketFactory = context.getSocketFactory();
        hostnameVerifier = (hostname, session) -> true;
      }
    } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException
            | KeyManagementException | IOException e) {
      Log.e("Certificates", "Error generating certificates", e);
      Log.ui("ERR_CERT", S.ERROR_CERTS);
      return false;
    }
    return true;
  }

  private String request(URL url) throws IOException, UnknownHostException {
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setConnectTimeout(3000);
    conn.setReadTimeout(5000);
    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
    StringBuilder buffer = new StringBuilder();
    String input;

    while ((input = in.readLine()) != null) { //read IP address
    buffer.append(input);
    }
    in.close();
    conn.disconnect();
    return buffer.toString();
  }

  /**
   * Get IP of user's device.
   *
   * @param port port to run replays
   * @return user's public IPs or -1 if cannot connect to the server
   */
  private Server getPublicIP(String port) {
    Server publicIP = new Server("", "");

    if (servers.size() != 0 && !servers.get(0).getIpv4().equals("127.0.0.1")) {
      String url4 = "http://" + servers.get(0).getIpv4() + ":" + port + "/WHATSMYIPMAN";
      String url6 = "http://[" + servers.get(0).getIpv6() + "]:" + port + "/WHATSMYIPMAN";
      Log.d("getPublicIP", "url4: " + url4);

      int numFails = 0;
      while (!publicIP.hasIpv4()) {
        try {
          Server ipv4 = new Server(request(new URL(url4)));

          if (!ipv4.hasIpv4()) {
            Log.e("getPublicIP", "wrong format of public IP: ipv4: " + ipv4);
            throw new UnknownHostException();
          }

          publicIP.setIpv4(ipv4.getIpv4());
          Log.d("getPublicIP", "public IP: " + publicIP);
        } catch (UnknownHostException e) {
          Log.w("getPublicIP", "failed to get public IPv4!", e);
          break;
        } catch (IOException e) {
          Log.w("getPublicIP", "Can't connect to server");
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e1) {
            Log.w("getPublicIP", "Sleep interrupted", e1);
          }
          if (++numFails == 5) { //Cannot connect to server after 5 tries
            Log.w("getPublicIP", "Cannot connect to server after 5 tries", e);
            return new Server("", "");
          }
        }
      }

      Log.d("getPublicIP", "url6: " + url6);
      numFails = 0;
      while (!publicIP.hasIpv6()) {
        try {
          Server ipv6 = new Server(request(new URL(url6)));

          if (!ipv6.hasIpv6()) {
            Log.e("getPublicIP", "wrong format of public IPv6: " + ipv6);
            throw new UnknownHostException();
          }

          publicIP.setIpv6(ipv6.getIpv6());
          Log.d("getPublicIP", "public IP: " + publicIP);
        } catch (UnknownHostException e) {
          Log.w("getPublicIP", "failed to get public IP!", e);
          break;
        } catch (IOException e) {
          Log.w("getPublicIP", "Can't connect to server");
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e1) {
            Log.w("getPublicIP", "Sleep interrupted", e1);
          }
          if (++numFails == 5) { //Cannot connect to server after 5 tries
            Log.w("getPublicIP", "Cannot connect to server after 5 tries", e);
            return publicIP;
          }
        }
      }
    } else {
      Log.w("getPublicIP", "Client IP is not available");
      publicIP = new Server("", "");
    }
    return publicIP;
  }

  /**
   * Send a GET or POST request to the server.
   *
   * @param url    URL to the server
   * @param method either GET or POST
   * @param main   true if request is to main server; false otherwise
   * @param data   data to send to server in a GET request, null if a POST request or if no data to
   *               send to server
   * @param pairs  data to send to server in a POST request, null if a GET request
   * @return a response from the server in the form of a JSONObject
   */
  private JSONObject sendRequest(String url, String method, boolean main,
                                 ArrayList<String> data, HashMap<String, String> pairs) {
    final JSONObject[] json = {null};
    final HttpsURLConnection[] conn = new HttpsURLConnection[1];
    final boolean[] readyToReturn = {false};
    Thread serverComm = new Thread(new Runnable() {
      @Override
      public void run() {
        String url_string = url;
        if (method.equalsIgnoreCase("GET")) {
          if (data != null) {
            String dataURL = URLEncoder(data);
            url_string += "?" + dataURL;
          }
          Log.d("Send GET Request", url_string);

          for (int i = 0; i < 3; i++) {
            try {
              //connect to server
              URL u = new URL(url_string);
              //send data to server
              conn[0] = (HttpsURLConnection) u.openConnection();
              if (main && hostnameVerifier != null && sslSocketFactory != null) {
                conn[0].setHostnameVerifier(hostnameVerifier);
                conn[0].setSSLSocketFactory(sslSocketFactory);
              }
              conn[0].setConnectTimeout(8000);
              conn[0].setReadTimeout(8000);
              BufferedReader in = new BufferedReader(new InputStreamReader(
                      conn[0].getInputStream()));
              StringBuilder buffer = new StringBuilder();
              String input;

              // parse BufferReader rd to StringBuilder res
              while ((input = in.readLine()) != null) { //read response from server
                buffer.append(input);
              }

              in.close();
              conn[0].disconnect();
              json[0] = new JSONObject(buffer.toString()); // parse String to json file
              break;
            } catch (IOException e) {
              Log.e("Send Request", "sendRequest GET failed", e);
            } catch (JSONException e) {
              Log.e("Send Request", "JSON Parse failed", e);
            }
          }
        } else if (method.equalsIgnoreCase("POST")) {
          Log.d("Send POST Request", url_string);

          try {
            //connect to server
            URL u = new URL(url_string);
            conn[0] = (HttpsURLConnection) u.openConnection();
            conn[0].setHostnameVerifier(hostnameVerifier);
            conn[0].setSSLSocketFactory(sslSocketFactory);
            conn[0].setConnectTimeout(5000);
            conn[0].setReadTimeout(5000);
            conn[0].setRequestMethod("POST");
            conn[0].setDoInput(true);
            conn[0].setDoOutput(true);

            OutputStream os = conn[0].getOutputStream();
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(os, StandardCharsets.UTF_8));
            writer.write(paramsToPostData(pairs)); //send data to server

            writer.flush();
            writer.close();
            os.close();

            BufferedReader in = new BufferedReader(new InputStreamReader(
                    conn[0].getInputStream()));
            StringBuilder buffer = new StringBuilder();
            String input;

            // parse BufferReader rd to StringBuilder res
            while ((input = in.readLine()) != null) { //read response from server
              buffer.append(input);
            }
            in.close();
            conn[0].disconnect();
            json[0] = new JSONObject(buffer.toString()); // parse String to json file.
          } catch (JSONException e) {
            Log.e("Send Request", "convert string to json failed", e);
            json[0] = null;
          } catch (IOException e) {
            Log.e("Send Request", "sendRequest POST failed", e);
            json[0] = null;
          }
        }
        readyToReturn[0] = true;
      }
    });
    serverComm.start();
    Timer t = new Timer();
    //timeout server after 8 sec; server timeout field times out only when nothing is sent;
    //if stuff sends too slowly, it could take forever, so this external timer prevents that
    t.schedule(new TimerTask() {
      @Override
      public void run() { //set timer to timeout the thread if max time has been reached for replay
        if (conn[0] != null) {
          conn[0].disconnect();
        }
        readyToReturn[0] = true;
      }
    }, 8000);
    timers.add(t);
    //wait until ready to move on (i.e. when result retrieved or timeout), as threads don't
    //block execution
    while (!readyToReturn[0]) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Log.w("Send Request", "Interrupted", e);
      }
    }
    return json[0];
  }

  /**
   * Overload URLEncoder to encode map to a url for a GET request.
   *
   * @param map data to be converted into a string to send to the server
   * @return encoded string containing data to send to server
   */
  private String URLEncoder(ArrayList<String> map) {
    StringBuilder data = new StringBuilder();
    for (String s : map) {
      if (data.length() > 0) {
        data.append("&");
      }
      data.append(s);
    }
    return data.toString();
  }

  /**
   * Encodes data into a string to send POST request to server.
   *
   * @param params data to convert into string to send to server
   * @return an encoded string to send to the server
   */
  private String paramsToPostData(HashMap<String, String> params) {
    StringBuilder result = new StringBuilder();
    boolean first = true;
    for (Map.Entry<String, String> entry : params.entrySet()) {
      if (first) {
        first = false;
      } else {
        result.append("&");
      }

      result.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
      result.append("=");
      result.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
    }
    return result.toString();
  }

  /**
   * Reads the replay files and loads them into memory as a bean.
   *
   * @param filename filename of the replay
   * @return a bean containing information about the replay
   */
  private CombinedAppJSONInfoBean unpickleJSON(String filename) {
    InputStream inputStream;
    CombinedAppJSONInfoBean appData = new CombinedAppJSONInfoBean(); //info about replay
    ArrayList<RequestSet> Q = new ArrayList<>(); //list of packets for replay
    try {
      inputStream = new FileInputStream(filename); //open replay file
      int size = inputStream.available();
      byte[] buffer = new byte[size];
      inputStream.read(buffer);
      inputStream.close();

      //convert file contents to JSONArray object
      String jsonStr = new String(buffer, StandardCharsets.UTF_8);
      JSONArray json = new JSONArray(jsonStr);

      JSONArray qArray = (JSONArray) json.get(0); //the packets in a replay file
      for (int i = 0; i < qArray.length(); i++) {
        RequestSet tempRS = new RequestSet();
        JSONObject dictionary = qArray.getJSONObject(i);
        tempRS.setc_s_pair(dictionary.getString("c_s_pair")); //client-server pair
        tempRS.setPayload(UtilsManager.hexStringToByteArray(dictionary.getString("payload")));
        tempRS.setTimestamp(dictionary.getDouble("timestamp"));

        //for tcp
        if (dictionary.has("response_len")) { //expected length of response
          tempRS.setResponse_len(dictionary.getInt("response_len"));
        }
        if (dictionary.has("response_hash")) {
          tempRS.setResponse_hash(dictionary.get("response_hash").toString());
        }
        //for udp
        if (dictionary.has("end"))
          tempRS.setEnd(dictionary.getBoolean("end"));

        Q.add(tempRS);
      }

      appData.setQ(Q);

      //udp
      JSONArray portArray = (JSONArray) json.get(1); //udp client ports
      ArrayList<String> portStrArray = new ArrayList<>();
      for (int i = 0; i < portArray.length(); i++) {
        portStrArray.add(portArray.getString(i));
      }
      appData.setUdpClientPorts(portStrArray);

      //for tcp
      JSONArray csArray = (JSONArray) json.get(2); //c_s_pairs
      ArrayList<String> csStrArray = new ArrayList<>();
      for (int i = 0; i < csArray.length(); i++) {
        csStrArray.add(csArray.getString(i));
      }
      appData.setTcpCSPs(csStrArray);
      appData.setReplayName(json.getString(3)); //name of replay
      appData.setUdpCSPnum(json.getInt(4));
    } catch (JSONException | IOException e) {
      Log.e("UnpickleJSON", "Error reading test files", e);
    }
    return appData;
  }

  /**
   * Run test. This method is called for every app/port the user selects. It is also called if
   * differentiation is detected for a test, and confirmation setting is enabled to run a second
   * test for the app/port to confirm if there is differentiation.
   * <p>
   * Each test has two replays. For apps, the replays consist of the original replay, which contains
   * actual traffic from that app, and a random replay, which replaces the content of the original
   * replay with random traffic. For ports, the "original" replay is the port that is being tested.
   * The "random" replay is port 443. The method uses "open" to denote the "original" replay and
   * "random" to denote the "random" replay.
   * <p>
   * There are three main steps in this method: Step A: Flip a coin to decide which replay type to
   * run first. Step B: Run replays. Step C: Determine if there is differentiation.
   * <p>
   * Step B has several sub-steps which run for each replay: Step 0: Initialize variables. Step 1:
   * Tell server about the replay that is about to happen. Step 2: Ask server for permission to run
   * replay. Step 3: Send noIperf. Step 4: Send device info. Step 5: Get port mapping from server.
   * Step 6: Create TCP clients from CSPairs and UDP clients from client ports. Step 7: Start
   * notifier for UDP. Step 8: Start receiver to log throughputs on a given interval. Step 9: Send
   * packets to server. Step 10: Tell server that replay is finished. Step 11: Send throughputs and
   * slices to server. Step 12: Close side channel and TCP/UDP sockets.
   *
   * @return 0 if successful; error code otherwise
   */
  private int runTest() {
    boolean portBlocked = false;

    /*
     * Step 0: Initialize variables.
     */
    // Based on the type selected load open or random trace of given application
    this.appData = unpickleJSON(app.getDataFile());

    try {
      Log.ui("updateStatus", S.CREATE_SIDE_CHANNEL);
      int sideChannelPort = Config.combined_sidechannel_port;

      Log.d("Servers", servers + " metadata " + metadataServer);
      // This side channel is used to communicate with the server in bytes mode and to
      // run traces, it send tcp and udp packets and receives the same from the server
      //Server handles communication in handle() function in server_replay.py in server
      //code
      ArrayList<CombinedSideChannel> sideChannels = new ArrayList<>();
      int id = 0;
      //each concurrent test gets its own sidechannel because each concurrent test is run on
      //a different server
      for (Server server : servers) {
        sideChannels.add(new CombinedSideChannel(id, sslSocketFactory,
                server.getIpv4(), sideChannelPort, appData.isTCP()));
        id++;
      }

      ArrayList<JitterBean> jitterBeans = new ArrayList<>();
      for (CombinedSideChannel ignored : sideChannels) {
        jitterBeans.add(new JitterBean());
      }

      //Get user's IP address
      String replayPort = "80";
      Server ipThroughProxy = new Server("127.0.0.1", "");
      if (appData.isTCP()) {
        for (String csp : appData.getTcpCSPs()) {
          replayPort = csp.substring(csp.lastIndexOf('.') + 1);
        }
        ipThroughProxy = getPublicIP(replayPort);
        if (!ipThroughProxy.hasIp()) { //port is blocked; move on to next replay
          portBlocked = true;
          return 0;
        }
      }

      // testId is how server knows if the trace ran was open or random
      testId = 0;

      if (doTest) {
        Log.w("Replay", "include -Test string");
      }

      /*
       * Step 1: Tell server about the replay that is about to happen.
       */
      int i = 0;
      String realIps = ipThroughProxy.getIpv4() + "|" + ipThroughProxy.getIpv6(); // TODO
      for (CombinedSideChannel sc : sideChannels) {
        // This is group of values that is used to track traces on server
        // Youtube;False;0;DiffDetector;0;129.10.9.93;1.0
        //set extra string to number tries needed to access MLab server
        Config.extraString = numMLab.size() == 0 ? "0" : numMLab.get(i).toString();
        sc.declareID(appData.getReplayName(), "True",
                randomID, String.valueOf(historyCount), String.valueOf(testId),
                doTest ? Config.extraString + "-Test" : Config.extraString,
                realIps, Consts.VERSION_NAME);

        // This tuple tells the server if the server should operate on packets of traces
        // and if so which packets to process
        sc.sendChangeSpec(-1, "null", "null");
        i++;
      }

      /*
       * Step 2: Ask server for permission to run replay.
       */
      Log.ui("updateStatus", S.ASK4PERMISSION);
      // Now to move forward we ask for server permission
      ArrayList<Integer> numOfTimeSlices = new ArrayList<>();
      for (CombinedSideChannel sc : sideChannels) {
        String[] permission = sc.ask4Permission();
        String status = permission[0].trim();

        Log.d("Replay", "Channel " + sc.getId() + ": permission[0]: " + status
                + " permission[1]: " + permission[1]);

        String permissionError = permission[1].trim();
        if (status.equals("0")) {
          int errorCode;
          // These are the different errors that server can report
          switch (permissionError) {
            case "1": //server cannot identify replay
              Log.ui("ERR_PERM_REPLAY", S.ERROR_UNKNOWN_REPLAY);
              errorCode = Consts.ERR_PERM_REPLAY;
              break;
            case "2": //only one replay can run at a time per IP
              Log.ui("ERR_PERM_IP", S.ERROR_IP_CONNECTED);
              errorCode = Consts.ERR_PERM_IP;
              break;
            case "3": //server CPU > 95%, disk > 95%, or bandwidth > 2000 Mbps
              Log.ui("ERR_PERM_RES", S.ERROR_LOW_RESOURCES);
              errorCode = Consts.ERR_PERM_RES;
              break;
            default:
              Log.ui("ERR_PERM_UNK", S.ERROR_UNKNOWN);
              errorCode = Consts.ERR_PERM_UNK;
          }
          return errorCode;
        }

        numOfTimeSlices.add(Integer.parseInt(permission[2].trim(), 10));
      }

      /*
       * Step 3: Send noIperf.
       */
      for (CombinedSideChannel sc : sideChannels) {
        sc.sendIperf(); // always send noIperf here
      }

      /*
       * Step 4: Send device info.
       */
      for (CombinedSideChannel sc : sideChannels) {
        sc.sendMobileStats(Config.sendMobileStats);
      }

      /*
       * Step 5: Get port mapping from server.
       */
      /*
       * Ask for port mapping from server. For some reason, port map
       * info parsing was throwing error. so, I put while loop to do
       * this until port mapping is parsed successfully.
       */
      Log.ui("updateStatus", S.RECEIVE_SERVER_PORT_MAPPING);

      ArrayList<HashMap<String, HashMap<String, HashMap<String, ServerInstance>>>> serverPortsMaps
              = new ArrayList<>();
      ArrayList<UDPReplayInfoBean> udpReplayInfoBeans = new ArrayList<>();
      for (CombinedSideChannel sc : sideChannels) {
              serverPortsMaps.add(sc.receivePortMappingNonBlock());
        UDPReplayInfoBean udpReplayInfoBean = new UDPReplayInfoBean();
        udpReplayInfoBean.setSenderCount(sc.receiveSenderCount());
        udpReplayInfoBeans.add(udpReplayInfoBean);
        Log.i("Replay", "Channel " + sc.getId() + ": Successfully received"
                + " serverPortsMap and senderCount!");
      }

      /*
       * Step 6: Create TCP clients from CSPairs and UDP clients from client ports.
       */
      Log.ui("updateStatus", S.CREATE_TCP_CLIENT);

      //map of all cs pairs to TCP clients for a replay
      ArrayList<HashMap<String, CTCPClient>> CSPairMappings = new ArrayList<>();

      //create TCP clients
      for (CombinedSideChannel sc : sideChannels) {
        HashMap<String, CTCPClient> CSPairMapping = new HashMap<>();
        for (String csp : appData.getTcpCSPs()) {
          //get server IP and port
          String destIP = csp.substring(csp.lastIndexOf('-') + 1,
                  csp.lastIndexOf("."));

          if (!Config.publicIP.hasIpv6() && destIP.contains(":")) {
              continue;
          }

          String destPort = csp.substring(csp.lastIndexOf('.') + 1);
          //pad port to 5 digits with 0s; ex. 00443 or 00080
          destPort = String.format("%5s", destPort).replace(' ', '0');

          //get the server
          ServerInstance instance;
          try {
            instance = Objects.requireNonNull(Objects.requireNonNull(
                    serverPortsMaps.get(sc.getId()).get("tcp")).get(destIP)).get(destPort);
            assert instance != null;
          } catch (NullPointerException | AssertionError e) {
            Log.e("Replay", "Cannot get instance", e);
            Log.ui("ERR_CONN_INST", S.ERROR_NO_CONNECTION);
            return Consts.ERR_CONN_INST;
          }
          if (!instance.server.hasIp()) {
            // Use a setter instead probably
            instance.server = servers.get(sc.getId()); // serverPortsMap.get(destPort);
          }

          //create the client
          CTCPClient c = new CTCPClient(csp, instance.server,
                  Integer.parseInt(instance.port),
                  appData.getReplayName(), Config.publicIP);
          CSPairMapping.put(csp, c);
        }
        CSPairMappings.add(CSPairMapping);
        Log.i("Replay", "Channel " + sc.getId() + ": created clients from CSPairs");
        Log.d("Replay", "Channel " + sc.getId() + ": Size of CSPairMapping is "
                + CSPairMapping.size());
      }

      Log.ui("updateStatus", S.CREATE_UDP_CLIENT);

      //map of all client ports to UDP clients for a replay
      ArrayList<HashMap<String, CUDPClient>> udpPortMappings = new ArrayList<>();

      //create client for each UDP port
      for (CombinedSideChannel sc : sideChannels) {
        HashMap<String, CUDPClient> udpPortMapping = new HashMap<>();
        udpPortMappings.add(udpPortMapping);
        Log.d("Replay", "Channel " + sc.getId() + ": Size of udpPortMapping is "
                + udpPortMapping.size());
      }

      /*
       * Step 7: Start notifier for UDP.
       */
      Log.ui("updateStatus", S.RUN_NOTF);

      ArrayList<CombinedNotifierThread> notifiers = new ArrayList<>();
      ArrayList<Thread> notfThreads = new ArrayList<>();
      for (CombinedSideChannel sc : sideChannels) {
        CombinedNotifierThread notifier = sc.notifierCreator(udpReplayInfoBeans.get(sc.getId()), this.appData.getUdpCSPnum());
        notifiers.add(notifier);
        Thread notfThread = new Thread(notifier);
        notfThread.start();
        notfThreads.add(notfThread);
      }

      /*
       * Step 8: Start receiver to log throughputs on a given interval.
       */
      Log.ui("updateStatus", S.RUN_RECEIVER);

      ArrayList<CombinedAnalyzerTask> analyzerTasks = new ArrayList<>();
      ArrayList<Timer> analyzerTimers = new ArrayList<>();
      ArrayList<CombinedReceiverThread> receivers = new ArrayList<>();
      ArrayList<Thread> rThreads = new ArrayList<>();
      for (CombinedSideChannel sc : sideChannels) {
        CombinedAnalyzerTask analyzerTask = new CombinedAnalyzerTask(app.getTime() / 2.0,
                appData.isTCP(), numOfTimeSlices.get(sc.getId()), runPortTests); //throughput logged
        Timer analyzerTimer = new Timer(true); //timer to log throughputs on interval
        analyzerTimer.scheduleAtFixedRate(analyzerTask, 0, analyzerTask.getInterval());
        analyzerTasks.add(analyzerTask);
        analyzerTimers.add(analyzerTimer);

        CombinedReceiverThread receiver = new CombinedReceiverThread(
                udpReplayInfoBeans.get(sc.getId()), jitterBeans.get(sc.getId()), analyzerTask); //receiver for udp
        receivers.add(receiver);
        Thread rThread = new Thread(receiver);
        rThread.start();
        rThreads.add(rThread);
      }

      /*
       * Step 9: Send packets to server.
       */
      Log.ui("updateStatus", S.RUN_SENDER);

      CombinedQueue queue = new CombinedQueue(appData.getQ(), jitterBeans, analyzerTasks,
              runPortTests ? Consts.REPLAY_PORT_TIMEOUT : Consts.REPLAY_APP_TIMEOUT);
      long timeStarted = System.nanoTime(); //start time for sending
      //send packets
      ArrayList<HashMap<String, HashMap<String, ServerInstance>>> udpServerMappings = new ArrayList<>();
      for (HashMap<String, HashMap<String, HashMap<String, ServerInstance>>> m : serverPortsMaps) {
        udpServerMappings.add(m.get("udp"));
      }

      queue.run(CSPairMappings, udpPortMappings, udpReplayInfoBeans, udpServerMappings,
              Config.timing, servers);

      //all packets sent - stop logging and receiving
      queue.stopTimers();
      for (Timer t : analyzerTimers) {
        t.cancel();
      }
      for (CombinedNotifierThread n : notifiers) {
        n.doneSending = true;
      }
      for (Thread t : notfThreads) {
        t.join();
      }
      for (CombinedReceiverThread r : receivers) {
        r.keepRunning = false;
      }
      for (Thread t : rThreads) {
        t.join();
      }

      /*
       * Step 10: Tell server that replay is finished.
       */
      Log.ui("updateStatus", S.SEND_DONE);

      //time to send all packets
      double duration = ((double) (System.nanoTime() - timeStarted)) / 1000000000;
      for (CombinedSideChannel sc : sideChannels) {
        sc.sendDone(duration);
      }
      Log.d("Replay", "replay finished using time " + duration + " s");

      /*
       * Step 11: Send throughputs and slices to server.
       */
      for (CombinedSideChannel sc : sideChannels) {
        sc.sendTimeSlices(analyzerTasks.get(sc.getId()).getAverageThroughputsAndSlices());
      }

      // TODO find a better way to do this
      // Send Result;No and wait for OK before moving forward
      for (CombinedSideChannel sc : sideChannels) {
        while (sc.getResult(Config.result)) {
          Thread.sleep(500);
        }
      }

      /*
       * Step 12: Close side channel and TCP/UDP sockets.
       */
      // closing side channel socket
      for (CombinedSideChannel sc : sideChannels) {
        sc.closeSideChannelSocket();
      }

      //close TCP sockets
      for (HashMap<String, CTCPClient> mapping : CSPairMappings) {
        for (String csp : appData.getTcpCSPs()) {
          CTCPClient c = mapping.get(csp);
          if (c != null) {
            c.close();
          }
        }
      }
      Log.i("CleanUp", "Closed CSPairs 1");

      //close UDP sockets
      for (HashMap<String, CUDPClient> mapping : udpPortMappings) {
        for (String originalClientPort : appData.getUdpClientPorts()) {
          CUDPClient c = mapping.get(originalClientPort);
          if (c != null) {
            c.close();
          }
        }
      }

      Log.i("CleanUp", "Closed CSPairs 2");
    } catch (InterruptedException e) {
      Log.w("Replay", "Replay interrupted!", e);
    } catch (IOException e) { //something wrong with receiveKbytes() or constructor in CombinedSideChannel
      Log.e("Replay", "Some IO issue with server", e);
      Log.ui("ERR_CONN_IO_SERV", S.ERROR_NO_CONNECTION);
      return Consts.ERR_CONN_IO_SERV;
    }

    return 0;
  }
}
