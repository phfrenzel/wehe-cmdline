package mobi.meddle.wehe.constant;

public final class S {
  public static final String PORT_NAME = "Port %s";

  public static final String ERROR_IO_INFO = "IO error related to info file.";
  public static final String ERROR_JSON = "JSON error.";
  public static final String ERROR_NO_CONNECTION = "Cannot connect to server, try again later.";
  public static final String ERROR_NO_WS = "Cannot connect to WebSocket.";
  public static final String ERROR_UNKNOWN_HOST = "Cannot find server. Try again later or try another server.";
  public static final String ERROR_UNKNOWN_META_HOST = "Cannot find metadata server. Try again later.";
  public static final String ERROR_CERTS = "Error with certificates.";
  public static final String ERROR_NO_USER_ID = "Cannot find the user ID.";
  public static final String ERROR_UNKNOWN_TEST = "No such test exists.";
  public static final String ERROR_UNKNOWN_REPLAY = "Replay does not match the replay on the server.";
  public static final String ERROR_IP_CONNECTED = "A client with this IP is already connected.";
  public static final String ERROR_LOW_RESOURCES = "Server is low on resources, try again later.";
  public static final String ERROR_UNKNOWN = "Unknown server error";
  public static final String ERROR_ANALYSIS_FAIL = "Unable to analyze replay.";
  public static final String ERROR_RESULT = "Error getting results.";

  public static final String CREATE_SIDE_CHANNEL = "Creating side channel";
  public static final String ASK4PERMISSION = "Asking for permission";
  public static final String RECEIVE_SERVER_PORT_MAPPING = "Receiving server port mapping";
  public static final String CREATE_TCP_CLIENT = "Creating all TCP client sockets";
  public static final String CREATE_UDP_CLIENT = "Creating all UDP client sockets";
  public static final String RUN_NOTF = "Starting side channel notifier";
  public static final String RUN_RECEIVER = "Starting the Receiver process";
  public static final String RUN_SENDER = "Running the Sender process";
  public static final String SEND_DONE = "Notifying server that replay is complete";
  public static final String WAITING = "Waiting for server result";
  public static final String CONFIRMATION_REPLAY = "Possible differentiation, re-running test to confirm";

  public static final String NO_DIFF = "No differentiation";
  public static final String HAS_DIFF = "Differentiation detected";
  public static final String INCONCLUSIVE = "Results inconclusive, try running the test again";

  public static final String TEST_BLOCKED_APP_TEXT = "The test appears to have been blocked. This "
          + "blockage might come from your ISP or an equipment on your local network or the server side.";
  public static final String TEST_BLOCKED_PORT_TEXT = "HTTPS traffic on this port might be blocked."
          + " This blockage might come from your ISP or an equipment on your local network or the server side.";
  public static final String TEST_PRIORITIZED_APP_TEXT = "The test appears to have been prioritized."
          + " This prioritization might come from your ISP or an equipment on your local network or the server side.";
  public static final String TEST_PRIORITIZED_PORT_TEXT = "HTTPS traffic on this port might be"
          + " prioritized. This prioritization might come from your ISP or an equipment on your"
          + " local network or the server side.";
  public static final String TEST_THROTTLED_APP_TEXT = "The test appears to have been throttled."
          + " This throttling might come from your ISP or an equipment on your local network or the"
          + " server side.\n\n360p: 0.4–1.0 Mbps\n480p: 0.5–2.0 Mbps\n720p: 1.5–4.0 Mbps\n1080p: 3.0–6.0 Mbps";
  public static final String TEST_THROTTLED_PORT_TEXT = "HTTPS traffic on this port might be"
          + " throttled. This throttling might come from your ISP or an equipment on your local"
          + " network or the server side.\n\n360p: 0.4–1.0 Mbps\n480p: 0.5–2.0 Mbps\n720p:"
          + " 1.5–4.0 Mbps\n1080p: 3.0–6.0 Mbps";
  public static final String NOT_ALL_TCP_SENT_TEXT = "It seems that Wehe could not reach a result. "
          + "If this situation persists, it might come from your ISP or an equipment on your local "
          + "network or the server side.";

  public static final String REPLAY_FINISHED_TITLE = "Replays Finished!";

  public static final String USAGE = "Usage: java -jar wehe-cmdline.jar -n [TEST_NAME] [OPTION]...\n\n"
          + "Example: java -jar wehe-cmdline.jar -n applemusic -c -r results/\n\n"
          + "Options:\n"
          + "  -n TEST_NAME name of the test to run (required argument; see below for list of tests)\n"
          + "  -s SERV_NAME hostname or IP of server to run the tests (Default: wehe4.meddle.mobi)\n"
          + "  -t RESR_ROOT resources root containing apps_list.json and the tests (Default: res/)\n"
          + "  -r RSLT_ROOT results root containing the result logs and info (Default: test_results/)\n"
          + "  -l LOG_LEVEL level of logs and above that should be printed to console (all levels will\n"
          + "                 be saved to the logs on disk regardless of the level printed to console);\n"
          + "                 either wtf, error, warn, info, or debug (Default: none of these, only UI logs)\n"
          + "  -h           print this help message\n"
          + "  -v           print the version number\n\n"
          + "| Test Names (`-n` arg)         |\n"
          + "|-------------------------------|\n"
          + "| fm4_favorite_1                |\n"
          + "| fm4_flow_1                    |\n"
          + "| fm4_misc_1                    |\n"
          + "| fm4_misc_2                    |\n"
          + "| fm4_programme_1               |\n"
          + "| fm4_radio_1                   |\n"
          + "| fm4_stories_scroll_1          |\n"
          + "| fm4web_misc_1                 |\n"
          + "| fm4web_misc_2                 |\n"
          + "| iphone_idle                   |\n"
          + "| snapchat_chat_1               |\n"
          + "| snapchat_map_1                |\n"
          + "| snapchat_misc                 |\n"
          + "| snapchat_snap_video_1         |\n"
          + "| snapchat_spotlight_1          |\n"
          + "| whatsapp_incoming_call_1      |\n"
          + "| whatsapp_incoming_video_1     |\n"
          + "| whatsapp_misc                 |\n"
          + "| whatsapp_outgoing_call_1      |\n"
          + "| whatsapp_outgoing_video_1     |\n"
          + "| fm4web_live_1_android         |\n"
          + "| fm4web_misc_1_android         |\n"
          + "| messenger_chat_1              |\n"
          + "| messenger_misc_1              |\n"
          + "| messenger_chat_1_android      |\n"
          + "| messenger_misc_1_android      |\n"
          + "| messenger_chat_1_android_ipv4 |\n"
          + "| messenger_misc_1_android_ipv4 |\n"
          + "| messenger_android_z_p4        |\n";
}
