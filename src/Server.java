import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

/**
 * Created by elkhan on 06/07/17.
 */
public class Server {
    private ByteBuffer byteBuffer;
    private ByteBuffer headerByteBuffer;
    private Selector channelSelector;
    private boolean isClosing = false;
    private ServerSocketChannel receivingSocketChannel;
    private HashMap<SocketChannel, SocketChannel> activeChannels;
    private HashMap<SocketChannel, Boolean> requests;
    private HashMap<String, LinkedList<SocketChannel>> waitingUploadingChannels;
    private HashMap<String, LinkedList<SocketChannel>> waitingDownloadingChannels;

    Server() throws IOException {
        activeChannels = new HashMap<>();
        waitingDownloadingChannels = new HashMap<>();
        waitingUploadingChannels = new HashMap<>();
        requests = new HashMap<>();

        byteBuffer = ByteBuffer.allocate(64 * 1024);
        headerByteBuffer = ByteBuffer.allocate(9);
        channelSelector = Selector.open();
        receivingSocketChannel = ServerSocketChannel.open();
        receivingSocketChannel.socket().bind(new InetSocketAddress(0));
        receivingSocketChannel.configureBlocking(false);

        PrintWriter writer = new PrintWriter("port", "UTF-8");
        writer.println(receivingSocketChannel.socket().getLocalPort());
        writer.close();
    }

    private void run() throws Exception {
        /*
         * First register main receiving channel.
         */
        receivingSocketChannel.register(channelSelector, SelectionKey.OP_ACCEPT);

        /*
         * Loop and process new requests.
         */
        while ( true ) {
            /*
             * Get currently available channels and process them.
             */
            channelSelector.select();
            Set<SelectionKey> selectedKeys = channelSelector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            while ( keyIterator.hasNext() ) {
                SelectionKey key = keyIterator.next();

                if ( key.isAcceptable() && !isClosing ) {
                    /*
                     * Get channel related with the key.
                     */
                    SocketChannel socketChannel = receivingSocketChannel.accept();

                    /*
                     * Add the new channel to the map of request channels and register for read.
                     */
                    socketChannel.configureBlocking(false);
                    requests.put(socketChannel, true);
                    socketChannel.register(channelSelector, SelectionKey.OP_READ);
                } else if ( key.isReadable() ) {
                    /*
                     * Get channel related with the key.
                     */
                    SocketChannel socketChannel = (SocketChannel) key.channel();

                    /*
                     * Check if the read is fired for request processing. If so process request.
                     */
                    if ( requests.containsKey(socketChannel) && !isClosing ) {
                        int numberOfBytesRead = socketChannel.read(headerByteBuffer);
                        if ( numberOfBytesRead == -1 ) {
                            socketChannel.close();
                            requests.remove(socketChannel);
                            System.out.println("Request header is not provided.");
                        } else {
                            Packet packet = Packet.getPacket(headerByteBuffer.array());
                            processRequest(packet, socketChannel);
                            requests.remove(socketChannel);

                            if ( isClosing ) {
                                socketChannel.close();
                                disconnectWaitingRequests();
                                receivingSocketChannel.close();
                            }
                        }
                        headerByteBuffer.clear();
                    } else if ( activeChannels.containsKey(socketChannel) ) {
                        SocketChannel downloadingSocketChannel = activeChannels.get(socketChannel);
                        transferData(socketChannel, downloadingSocketChannel);
                    }
                }

                keyIterator.remove();
            }

            if ( isClosing && activeChannels.isEmpty() ) {
                break;
            }
        }

        /*
         * Close all resources.
         */
        if ( receivingSocketChannel.isOpen() ) {
            receivingSocketChannel.close();
        }
    }

    private void disconnectWaitingRequests() throws IOException {
        /*
         * Close all waiting uploading channels.
         */
        for ( String key : waitingUploadingChannels.keySet() ) {
            LinkedList<SocketChannel> waitingChannels = waitingUploadingChannels.get(key);
            for (int i = 0; i < waitingChannels.size(); i++ ) {
                waitingChannels.get(i).close();
            }
        }

        /*
         * Close all waiting downloading channels.
         */
        for ( String key : waitingDownloadingChannels.keySet() ) {
            LinkedList<SocketChannel> waitingChannels = waitingDownloadingChannels.get(key);
            for (int i = 0; i < waitingChannels.size(); i++ ) {
                waitingChannels.get(i).close();
            }
        }

        /*
         * Close all requesting channels.
         */
        for ( SocketChannel key : requests.keySet() ) {
            key.close();
        }
    }

    private void processRequest(Packet packet, SocketChannel socketChannel) throws Exception {
        /*
         * Parse by command.
         */
        if ( packet.getCommand() == 'F' ) {
            isClosing = true;
        } else if ( packet.getCommand() == 'G' ) {
            /*
             * First check if we can match with current uploader.
             */
            if ( waitingUploadingChannels.containsKey(packet.getKey()) ) {
                SocketChannel uploadingSocketChannel = waitingUploadingChannels.get(packet.getKey()).pop();
                activeChannels.put(uploadingSocketChannel, socketChannel);

                /*
                 * Start transferring data from uploading channel.
                 */
                transferData(uploadingSocketChannel, socketChannel);

                /*
                 * Check if no other upload requests are pending for this key.
                 */
                if ( waitingUploadingChannels.get(packet.getKey()).isEmpty() ) {
                    waitingUploadingChannels.remove(packet.getKey());
                }

                try {
                   /*
                    * Register uploading channel with selector.
                    */
                    uploadingSocketChannel.register(channelSelector, SelectionKey.OP_READ);
                } catch (Exception e) {
                    uploadingSocketChannel.close();
                    activeChannels.get(uploadingSocketChannel).close();
                    activeChannels.remove(uploadingSocketChannel);
                    System.out.println("Connection with uploader was lost.");
                    return;
                }
            } else if ( waitingDownloadingChannels.containsKey(packet.getKey()) ) {
                waitingDownloadingChannels.get(packet.getKey()).add(socketChannel);
            } else {
                LinkedList<SocketChannel> sameKeyDownloadRequests = new LinkedList<SocketChannel>();
                sameKeyDownloadRequests.add(socketChannel);
                waitingDownloadingChannels.put(String.valueOf(packet.getKey()), sameKeyDownloadRequests);
            }

            /*
             * Downloading channel does not need to be registered with selector, so unregister.
             */
            SelectionKey downloadingSocketChanelKey = socketChannel.keyFor(channelSelector);
            downloadingSocketChanelKey.cancel();
        } else if ( packet.getCommand() == 'P' ) {
            /*
             * First check if we can match with current downloader.
             */
            if ( waitingDownloadingChannels.containsKey(packet.getKey()) ) {
                SocketChannel downloadingSocketChannel = waitingDownloadingChannels.get(packet.getKey()).pop();
                activeChannels.put(socketChannel, downloadingSocketChannel);

                /*
                 * Start transferring data.
                 */
                transferData(socketChannel, downloadingSocketChannel);

                /*
                 * Check if no other download requests are pending for this key.
                 */
                if ( waitingDownloadingChannels.get(packet.getKey()).isEmpty() ) {
                    waitingDownloadingChannels.remove(packet.getKey());
                }
            } else if ( waitingUploadingChannels.containsKey(packet.getKey()) ) {
                waitingUploadingChannels.get(packet.getKey()).add(socketChannel);

                /*
                 * Unregister uploading channel until we find corresponding download request.
                 */
                SelectionKey uploadingSocketChanelKey = socketChannel.keyFor(channelSelector);
                uploadingSocketChanelKey.cancel();
            } else {
                LinkedList<SocketChannel> sameKeyUploadRequests = new LinkedList<SocketChannel>();
                sameKeyUploadRequests.add(socketChannel);
                waitingUploadingChannels.put(String.valueOf(packet.getKey()), sameKeyUploadRequests);

                /*
                 * Unregister uploading channel until we find corresponding download request.
                 */
                SelectionKey uploadingSocketChanelKey = socketChannel.keyFor(channelSelector);
                uploadingSocketChanelKey.cancel();
            }
        } else {
            System.out.println("Invalid packet.");
        }
    }

    private void transferData(SocketChannel uploadingSocketChannel, SocketChannel downloadingSocketChannel) throws IOException {
        int numberOfBytesRead;
        try {
            numberOfBytesRead = uploadingSocketChannel.read(byteBuffer);
        } catch (Exception e) {
            uploadingSocketChannel.close();
            activeChannels.get(uploadingSocketChannel).close();
            activeChannels.remove(uploadingSocketChannel);
            System.out.println("Connection with uploader was lost.");
            byteBuffer.clear();
            return;
        }

        if ( numberOfBytesRead == -1 ) {
            uploadingSocketChannel.close();
            activeChannels.get(uploadingSocketChannel).close();
            activeChannels.remove(uploadingSocketChannel);
        } else {
            byteBuffer.flip();
            while ( byteBuffer.hasRemaining() ) {
                try {
                    downloadingSocketChannel.write(byteBuffer);
                } catch (Exception e) {
                    uploadingSocketChannel.close();
                    activeChannels.get(uploadingSocketChannel).close();
                    activeChannels.remove(uploadingSocketChannel);
                    System.out.println("Connection with downloader was lost.");
                    byteBuffer.clear();
                    return;
                }
            }
        }
        byteBuffer.clear();
    }

    public static void main(String[] args) {
        try {
            Server receiver = new Server();
            receiver.run();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
