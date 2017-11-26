/**
 * Created by elkhan on 05/07/17.
 */
public class Client {
    public static void main(String[] args) {
        try {
            if ( args.length == 3 ) {
                Terminator terminator = new Terminator(args[0], Integer.valueOf(args[1]));
                terminator.terminate();
            } else if ( args.length == 5 ) {
                Downloader downloader = new Downloader(args[0], Integer.valueOf(args[1]), args[2], args[3], Integer.valueOf(args[4]));
                downloader.download();
            } else if ( args.length == 6 ) {
                Uploader uploader = new Uploader(args[0], Integer.valueOf(args[1]), args[2], args[3], Integer.valueOf(args[4]), Long.valueOf(args[5]));
                uploader.upload();
            } else {
                System.out.println("Incorrect number of arguments passed.");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
