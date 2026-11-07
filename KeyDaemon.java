import java.io.*;
import java.net.*;

public class KeyDaemon {
    public static void main(String[] args) {
        System.out.println("KEYDAEMON_STARTING");
        System.out.flush();
        try {
            ServerSocket server = new ServerSocket(18888, 50, InetAddress.getByName("127.0.0.1"));
            System.out.println("KEYDAEMON_READY_ON_18888");
            System.out.flush();
            while (true) {
                try {
                    Socket sock = server.accept();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                    String line = reader.readLine();
                    if (line != null && line.startsWith("KEY ")) {
                        int code = Integer.parseInt(line.substring(4).trim());
                        System.out.println("INJECT_KEY: " + code);
                        System.out.flush();
                        Process p = Runtime.getRuntime().exec(new String[]{"input", "keyevent", String.valueOf(code)});
                        p.waitFor();
                        sock.getOutputStream().write("OK\n".getBytes());
                        sock.getOutputStream().flush();
                    } else if (line != null && line.startsWith("TEXT ")) {
                        String text = line.substring(5);
                        System.out.println("INJECT_TEXT: " + text);
                        System.out.flush();
                        Process p = Runtime.getRuntime().exec(new String[]{"input", "text", text});
                        p.waitFor();
                        sock.getOutputStream().write("OK\n".getBytes());
                        sock.getOutputStream().flush();
                    } else if (line != null && "PING".equals(line.trim())) {
                        sock.getOutputStream().write("PONG\n".getBytes());
                        sock.getOutputStream().flush();
                    }
                    sock.close();
                } catch (Throwable t) {
                    System.err.println("Client handling error: " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
