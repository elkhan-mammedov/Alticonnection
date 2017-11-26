import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Created by elkhan on 06/07/17.
 */
public class Uploader extends AbstractClient {
    private long waitTime;
    private char[] key;

    public Uploader(String host, int port, String command, String fileName, int payloadSize, long waitTime) throws IOException {
        super(host, port);
        this.command = command;
        this.fileName = fileName;
        this.payloadSize = payloadSize;
        this.waitTime = waitTime;
        this.key = new char[8];
        this.command.getChars(1, command.length(), this.key, 0);
    }

    public void upload() throws IOException, InterruptedException {
        int fileLength;
        int currentPayloadSize;
        int numberOfPackets;
        byte[] data = new byte[payloadSize];
        BufferedInputStream bufferedInputStream = null;
        boolean readFromFile = !isInteger(fileName);

        /*
         * First send upload request.
         */
        Packet packet = new Packet('P', String.valueOf(this.key));
        byte[] packetStream = packet.createPacket();
        ByteBuffer byteBuffer = ByteBuffer.wrap(packetStream, 0, Packet.headerSize);
        clientSocketChannel.write(byteBuffer);

        /*
         * Then figure out size of file and number of packets we will send.
         */
        if ( readFromFile ) {
            File file = new File(fileName);
            bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
            fileLength = (int) file.length();
        } else {
            fileLength = Integer.valueOf(fileName);
        }
        numberOfPackets = (int) Math.ceil((double) fileLength / payloadSize);

        /*
         * Send packets.
         */
        for (int i = 0; i < numberOfPackets; i++) {
            if ( readFromFile ) {
                currentPayloadSize = bufferedInputStream.read(data);
            } else {
                currentPayloadSize = Math.min(payloadSize, fileLength - i * payloadSize);
            }

            packetStream = Arrays.copyOfRange(data,0, currentPayloadSize);
            byteBuffer = ByteBuffer.wrap(packetStream, 0, currentPayloadSize);

            if ( clientSocketChannel.isConnected() ) {
                clientSocketChannel.write(byteBuffer);
                Thread.sleep(waitTime);
            } else {
                break;
            }
        }

        /*
         * Close all resources.
         */
        clientSocketChannel.close();
        if ( readFromFile ) {
            bufferedInputStream.close();
        }
    }

    private boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
        } catch(NumberFormatException e) {
            return false;
        } catch(NullPointerException e) {
            return false;
        }
        return true;
    }
}
