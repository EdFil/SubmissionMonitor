import log.Log;
import manager.ConfigurationManager;
import manager.FTPManager;
import manager.OnEventReceived;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;

/**
 * Created by edgar on 7/9/15.
 */
public class Main {

    /**
     * Print how to run program and exit
     */
    static void usage() {
        System.err.println("usage: java -jar Monitor [-r] dir");
        System.exit(-1);
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws IOException {
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

        // Load and initialize the FTPManager
        if(!ConfigurationManager.getInstance().load("ftp.properties"))
            System.exit(-1);
        FTPManager.init();

        // Every time the configuration changes, run the FTPManager
        ConfigurationManager.getInstance().setOnConfigurationReload(new Runnable() {
            @Override
            public void run() {
                FTPManager.getInstance().startThread();
            }
        });

        // What to do when the Watcher detect file changes
        OnEventReceived eventDelegate = new OnEventReceived() {
            public void execute(WatchEvent.Kind<Path> eventKind, Path filePath) {
                File file = filePath.toFile();
                if (eventKind == ENTRY_CREATE) {

                    // If file is hidden ignore
                    if (file.getName().startsWith(".")) {
                        return;
                    }

                    // Send file
                    if(!file.isDirectory())
                        //TODO: Unpack, run, send results
                        Log.d(TAG, "Received a file '" + file + "'.");
                }

            }
        };

        // Run the main directory watcher
        WatchDir watchDir = new WatchDir(dir, true, eventDelegate);
        watchDir.processEvents();
    }

}
