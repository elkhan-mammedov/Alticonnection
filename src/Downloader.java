import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Created by elkhan on 06/07/17.
 */
public class Downloader extends AbstractClient {
    private char[] key;

    public Downloader(String host, int port, String command, String fileName, int payloadSize) throws IOException {
        super(host, port);
        this.command = command;
        this.fileName = fileName;
        this.payloadSize = payloadSize;
        this.key = new char[8];
        this.command.getChars(1, command.length(), this.key, 0);
    }

    public void download() throws IOException {
        /*
         * First send download request.
         */
        Packet packet = new Packet('G', String.valueOf(this.key));
        byte[] packetStream = packet.createPacket();
        ByteBuffer byteBuffer = ByteBuffer.wrap(packetStream, 0, Packet.headerSize);
        clientSocketChannel.write(byteBuffer);
        byteBuffer.clear();

        FileChannel fileChannel = new FileOutputStream(fileName).getChannel();
        byteBuffer = ByteBuffer.allocate(payloadSize);

        /*
         * Loop until connection is not closed to receive new packets.
         */
        while ( clientSocketChannel.read(byteBuffer) != -1 ) {
            byteBuffer.flip();
            while ( byteBuffer.hasRemaining() ) {
                fileChannel.write(byteBuffer);
            }
            byteBuffer.clear();
        }

        /*
         * Close all resources.
         */
        clientSocketChannel.close();
        fileChannel.close();
    }
}
