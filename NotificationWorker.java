package FileServer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class NotificationWorker extends Thread {
    private final Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;

    public NotificationWorker(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            Object obj = in.readObject();
            if (!(obj instanceof String)) {
                return;
            }
            username = ((String) obj).trim();
            if (username.isEmpty()) {
                return;
            }

            Server.registerNotifier(username, out);

            // Keep the connection alive until client disconnects
            while (true) {
                Object msg = in.readObject();
                if (msg instanceof String && ((String) msg).equalsIgnoreCase("DISCONNECT")) {
                    break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            // Client disconnected or stream error
        } finally {
            if (username != null) {
                Server.unregisterNotifier(username, out);
            }
            try {
                if (out != null) out.close();
                if (in != null) in.close();
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
