import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**
 * Created by elkhan on 06/07/17.
 */
public class Packet {
    private char command;
    private String key;
    static final int headerSize = 9;

    public Packet(char command, String key) {
        this.command = command;
        this.key = key;
    }

    public byte[] createPacket() {
        byte[] packet = new byte[headerSize];
        packet[0] = (byte) (command & 0x00FF);
        System.arraycopy(key.getBytes(), 0, packet, 1, 8);
        return packet;
    }

    public static Packet getPacket(byte[] packetStream) throws UnsupportedEncodingException {
        char command = (char) (packetStream[0] & 0x00FF);
        String key = new String(Arrays.copyOfRange(packetStream, 1, headerSize));
        return new Packet(command, key);
    }

    public char getCommand() {
        return command;
    }

    public String getKey() {
        return key;
    }
}
