import log.Log;
import manager.ConfigurationManager;
import org.apache.commons.configuration.event.ConfigurationListener;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;

/**
 * Created by edgar on 7/9/15.
 */
public class Main {

    /**
     * Print how to run program and exit
     */
    static void usage() {
        System.err.println("usage: java Monitor [-r] dir");
        System.exit(-1);
    }

    private static void printCommandsHelp(){
        System.out.println("Commands:");
        System.out.println("h - help");
        System.out.println("r - refresh");
        System.out.println("q - quit");
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws IOException {
//        // parse arguments
//        if (args.length == 0 || args.length > 2)
//            usage();
//        int dirArg = 0;
//        if (args[0].equals("-r")) {
//            if (args.length < 2)
//                usage();
//            dirArg++;
//        }
//
//        // register directory and process its events
//        GlobalSettings.init();
//        FTPUploader.getInstance();
//        Path dir = Paths.get(args[dirArg]);
//        new WatchDir(dir, true).processEvents();

        // parse arguments
        if (args.length == 0 || args.length > 2)
            usage();
        int dirArg = 0;
        if (args[0].equals("-r")) {
            if (args.length < 2)
                usage();
            dirArg++;
        }

        Path dir = Paths.get(args[dirArg]);

        ConfigurationManager.getInstance().load("test.txt");
        WatchDir watchDir = new WatchDir(dir, true);
        watchDir.processEvents();
    }

}

