/**
 * Created by edgar on 7/10/15.
 */
import log.Log;
import manager.FTPManager;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;



public class WatchDir {

    private static final String TAG = WatchDir.class.getSimpleName();

    private final Path mRootDir;
    private final WatchService mWatcher;
    private final Map<WatchKey, Path> mKeys;
    private final boolean isRecursive;
    private boolean debug = false;

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    /**
     * Creates a WatchService and registers the given directory
     */
    public WatchDir(Path dir, boolean recursive) throws IOException {
        mRootDir = dir;
        mWatcher = FileSystems.getDefault().newWatchService();
        mKeys = new HashMap<WatchKey, Path>();
        isRecursive = recursive;

        if (recursive) {
            registerAll(dir);
        } else {
            register(dir);
        }

        // enable trace after initial registration
        this.debug = true;
    }

    /**
     * Register the given directory with the WatchService
     */
    public void register(Path dir) throws IOException {
        WatchKey key = dir.register(mWatcher, ENTRY_CREATE /*, ENTRY_DELETE, ENTRY_MODIFY*/);
        if (debug) {
            Path prev = mKeys.get(key);
            if (prev == null) {
                System.out.format("register: %s\n", dir);
            } else {
                if (!dir.equals(prev)) {
                    System.out.format("update: %s -> %s\n", prev, dir);
                }
            }
        }
        mKeys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    public void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir,
                                                     BasicFileAttributes attrs) throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Process all events for keys queued to the watcher
     * @throws IOException
     */
    void processEvents() throws IOException {
        for (;;) {

            // wait for key to be signalled
            WatchKey key;
            try {
                key = mWatcher.take();
            } catch (InterruptedException x) {
                return;
            }

            Path dir = mKeys.get(key);
            if (dir == null) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                Kind<?> kind = event.kind();

                // TODO: Handle OVERFLOW maybe?
                if (kind == OVERFLOW) {
                    continue;
                }

                // Context for directory entry event is the file name of entry
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                if(kind.equals(ENTRY_CREATE) && Files.isRegularFile(child, NOFOLLOW_LINKS)){
                    File file = child.toFile();
                    if(!file.getName().startsWith(".")) {
                        Log.d(TAG, "NEW FILE " + file);
                        FTPManager.getInstance().sendFile(file, mRootDir.relativize(child).toString());
                    }
                }

                // print out event
                // System.out.format("%s: %s\n", event.kind().name(), child);

                // if directory is created, and watching recursively, then
                // register it and its sub-directories
                if (isRecursive && (kind == ENTRY_CREATE)) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            registerAll(child);
                        } else {
                            File file = child.toFile();
                            if(!file.getName().contains(".")) {
                                Log.d(TAG, "Sending " + file.getName());
                            }
                        }
                    } catch (IOException x) { /* Empty */ }
                }
            }

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                mKeys.remove(key);

                // all directories are inaccessible
                if (mKeys.isEmpty()) {
                    break;
                }
            }
        }

    }
}