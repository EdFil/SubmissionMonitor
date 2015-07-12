package manager;

import log.Log;

import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.function.Consumer;

/**
 * A class that can be used to define what to do when an event is received
 *
 * @author Edgar Santos <edfil221@gmail.com>
 */
public class OnEventReceived {

    public static final String TAG = OnEventReceived.class.getSimpleName();

    public void execute(WatchEvent.Kind<Path> eventKind, Path filePath) {
        Log.d(TAG, eventKind + " - " + filePath);
    }
}
