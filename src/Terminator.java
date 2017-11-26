import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by elkhan on 06/07/17.
 */
public class Terminator extends AbstractClient {
    public Terminator(String host, int port) throws IOException {
        super(host, port);
    }

    public void terminate() throws IOException {
        /*
         * First send termination request.
         */
        Packet packet = new Packet('F', String.valueOf(new char[8]));
        byte[] packetStream = packet.createPacket();
        ByteBuffer byteBuffer = ByteBuffer.wrap(packetStream, 0, Packet.headerSize);
        clientSocketChannel.write(byteBuffer);

        /*
         * Close all resources.
         */
        clientSocketChannel.close();
    }
}
