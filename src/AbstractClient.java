import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

/**
 * Created by elkhan on 06/07/17.
 */
public abstract class AbstractClient {
    protected String host;
    protected int port;
    protected SocketChannel clientSocketChannel;

    protected String command;
    protected String fileName;
    protected int payloadSize;

    public AbstractClient(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        this.clientSocketChannel = SocketChannel.open();
        this.clientSocketChannel.connect(new InetSocketAddress(InetAddress.getByName(host), this.port));
    }
}
