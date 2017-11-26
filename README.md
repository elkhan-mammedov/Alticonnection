# Alticonnection

Alticonnection provides an infrastructure that facilitates a file exchange between clients through server. Communication between client and server uses TCP.

## Technology
Java NIO
Event driven programming

## Server

Server can handle an arbitrary number of concurrent connections and file exchanges, only limited by system configuration or memory. The server is started without any parameters and creates a TCP socket at an OS-assigned port. It prints out the assigned port number and store it in a local file port, which is used when starting clients. The server listens on its main socket and accepts client connections as they arrive. Clients perform an upload or download operation, or instruct the server to terminate.
Both upload and download operations specify a key that is used to match clients with each other, i.e., a client asking for downloading with a specific key receives the file that another client uploads using that key. Files are not stored at the server, but instead clients wait for a match and then data is forwarded directly from the uploader to the downloader. Multiple clients might specify the same key and operation. The server always match a pair of uploader and downloader with the same key, but does not serve clients with the same key and operation in any particular order. The server supports concurrent file exchanges.
When the server receives the termination command from a client, it closes all waiting connections from unmatched clients and not accept any further connections. It completes ongoing file exchanges and terminate only after all file exchanges are finished.

## Communication

The data stream sent from the client to the server adheres to the following format:
    command 1 		ASCII character: G (get = download), P (put = upload), or F (finish = termination)
    key     8 		ASCII characters (padded at the end by '\0'-characters, if necessary)
In case of an upload, the above 9-byte control information is immediately followed by the binary data stream of
the file. In case of download, the server responds with the binary data stream of the file. When a client has
completed a file upload, it closes the connection. Then the server closes the download connection to the other
client.

## Client Program

The client takes up to 6 parameters and can be invoked in 3 different ways:
    1. terminate server: client <host> <port> F
    2. download: client <host> <port> G<key> <file name> <recv size>
    3. upload: client <host> <port> P<key> <file name> <send size> <wait time>

The client creates a TCP socket and connects to the server at <host> and <port>. It then transmits the command string given in the 3rd shell parameter to the server as described above, i.e., with padding. When transmitting an 'F' command, the client sends an empty key, i.e., 8 '\0'-characters. When requesting an upload or download, it reads data from or stores data to, respectively, the file specified in the 4th parameter. When uploading and the 4th parameter is given as an integer number, the number is taken as the virtual file size in bytes. In this case, the sender application does not transmit the contents of an actual file, but empty/random data equivalent to the virtual file size.

The 5th parameter specifies the size of the buffer that is transmitted during each individual write/send or read/recv system call during the file transfer - except for the final data chunk that might be smaller. When uploading a file, the 6th parameter specifies a wait time in milliseconds between subsequent write/send system calls.

This parameters allows for a simple form of rate control that is important to test the concurrency of the server.

## Design ideas/justifications:

Server:
I implemented server using event driven single-thread approach, because it is more efficient than a multi-threaded one. So here is the general architecture of my server:
1) I have a single receiving server socket channel running on OS assigned port.
2) I also use Java NIO(New I/O) library. One of the features of this library includes selectors. It is possible to register the socket channels with selector object with specified events that will eventually fire and you will be able to handle those events in appropriate manner. The way I use selectors is as follows:
	I) I register the main receiving channel with my selector with an accept event, so that once my server accepts new connection I handle this event appropriately. 
	II) Once new client connects to the server, we get socket channel for this connection and register this new socket channel with our selector with read event, meaning that once the client sends its request information we will be able to process it accordingly.
3) Now, once we processed client's request, we know which action does the client want to perform, whether it is download, upload or terminate. In general, there are four maps which are present on the server to manage connections. One serves purposes of processing requests, the other three are actually for managing active connections, waiting downloaders and waiting uploaders. Requests are processed as follows:
	I) Download command, we would first check if we have matching uploader in waiting uploaders map, then we put this key-value (uploader-downloader) pair into our active connections map and remove uploader from waiting uploaders map. If we don't have matching uploader we would put this downloader (key-downloader channel) into waiting downloaders map. Also, we deregister downloader channel from our selector, since downloaders will no longer send any data to server.
	II) Upload command, we would first check if we have matching downloader in waiting downloaders map, then we put this key-value (uploader-downloader) pair into our active connections map and remove downloader from waiting downloaders map. If we don't have matching downloader we would put this uploader (key-uploader channel) into waiting uploaders map.
	III) Terminate command, we just set the flag indicating that server goes down, and thus we no longer process any requests, but we finish the ongoing transactions, once they are done server exits.
4) I have one loop, on each iteration of which we select the channels that were fired with new events and process those events as discussed above.

Clients:
I have one AbstractClient class and three other concrete classes: Downloader, Uploader and Terminator. I also have Packet class, which is used is request creation process. Designs of clients are pretty simple, so I will discuss them briefly.
	Downloader - sends a request to download to the server, then waits to receive data.
	Uploader - sends upload requests and then sends data packets to server.
	Terminator - sends a termination request.

## P.S. I am using the following version of Java:

Java(TM) SE Runtime Environment (build 1.8.0_131-b11) and my programs work perfectly fine.
