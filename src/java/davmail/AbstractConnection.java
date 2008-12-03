package davmail;

import davmail.exchange.ExchangeSession;
import davmail.smtp.SmtpConnection;
import davmail.tray.DavGatewayTray;
import org.apache.commons.httpclient.util.Base64;

import java.io.*;
import java.net.Socket;

/**
 * Generic connection common to pop3 and smtp implementations
 */
public class AbstractConnection extends Thread {
    protected final Socket client;

    protected BufferedReader in;
    protected OutputStream os;
    // user name and password initialized through connection
    protected String userName = null;
    protected String password = null;
    // connection state
    protected int state = 0;
    // Exchange session proxy
    protected ExchangeSession session;

    // only set the thread name and socket
    public AbstractConnection(String name, Socket clientSocket) {
        super(name);
        this.client = clientSocket;
    }

    // Initialize the streams and set thread name
    public AbstractConnection(String name, Socket clientSocket, String encoding) {
        super(name + "-" + clientSocket.getPort());
        this.client = clientSocket;
        try {
            if (encoding == null) {
                //noinspection IOResourceOpenedButNotSafelyClosed
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            } else {
                in = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF-8"));
            }
            os = new BufferedOutputStream(client.getOutputStream());
        } catch (IOException e) {
            close();
            DavGatewayTray.error("Exception while getting socket streams", e);
        }
    }

    /**
     * Send message to client followed by CRLF.
     *
     * @param message message
     * @throws IOException on error
     */
    public void sendClient(String message) throws IOException {
        sendClient(null, message);
    }

    /**
     * Send prefix and message to client followed by CRLF.
     *
     * @param prefix  prefix
     * @param message message
     * @throws IOException on error
     */
    public void sendClient(String prefix, String message) throws IOException {
        StringBuffer logBuffer = new StringBuffer("> ");
        if (prefix != null) {
            logBuffer.append(prefix);
            os.write(prefix.getBytes());
        }
        logBuffer.append(message);
        DavGatewayTray.debug(logBuffer.toString());
        os.write(message.getBytes());
        os.write((char) 13);
        os.write((char) 10);
        os.flush();
    }

    /**
     * Send only bytes to client.
     *
     * @param messageBytes content
     * @throws IOException on error
     */
    public void sendClient(byte[] messageBytes) throws IOException {
        StringBuffer logBuffer = new StringBuffer("> ");
        logBuffer.append(new String(messageBytes));
        // TODO : create a wire debug level
        // DavGatewayTray.debug(logBuffer.toString());
        os.write(messageBytes);
        os.flush();
    }

    /**
     * Read a line from the client connection.
     * Log message to stdout
     *
     * @return command line or null
     * @throws IOException when unable to read line
     */
    public String readClient() throws IOException {
        String line = in.readLine();
        // TODO : add basic authorization check
        if (line != null) {
            if (line.startsWith("PASS")) {
                DavGatewayTray.debug("< PASS ********");
            } else if (state == SmtpConnection.PASSWORD) {
                DavGatewayTray.debug("< ********");
            } else if (line.startsWith("Authorization:")) {
                DavGatewayTray.debug("< Authorization: ********");
            } else {
                DavGatewayTray.debug("< " + line);
            }
        }
        DavGatewayTray.switchIcon();
        return line;
    }

    /**
     * Close client connection, streams and Exchange session .
     */
    public void close() {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e2) {
                DavGatewayTray.warn("Exception closing client input stream", e2);
            }
        }
        if (os != null) {
            try {
                os.close();
            } catch (IOException e2) {
                DavGatewayTray.warn("Exception closing client output stream", e2);
            }
        }
        try {
            client.close();
        } catch (IOException e2) {
            DavGatewayTray.warn("Exception closing client socket", e2);
        }
        try {
            if (session != null) {
                session.close();
            }
        } catch (IOException e3) {
            DavGatewayTray.warn("Exception closing gateway", e3);
        }
    }

    protected String base64Encode(String value) {
        return new String(Base64.encode(value.getBytes()));
    }

    protected String base64Decode(String value) throws IOException {
        return new String(Base64.decode(value.getBytes()));
    }
}
