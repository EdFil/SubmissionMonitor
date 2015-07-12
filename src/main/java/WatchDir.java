import log.Log;
import manager.OnEventReceived;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;



public class WatchDir {

    private static final String TAG = WatchDir.class.getSimpleName();

    private final Path mRootDir;
    private final WatchService mWatcher;
    private final Map<WatchKey, Path> mKeys;
    private final boolean isRecursive;
    private boolean debug = false;
    private OnEventReceived mEventReceivedDelegate;

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    /**
     * Creates a WatchService and registers the given directory
     */
    public WatchDir(Path dir, boolean recursive, OnEventReceived eventReceived) throws IOException {
        mRootDir = dir;
        mWatcher = FileSystems.getDefault().newWatchService();
        mKeys = new HashMap<>();
        isRecursive = recursive;
        mEventReceivedDelegate = (eventReceived != null) ? eventReceived : new OnEventReceived();

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

            for(WatchEvent<?> wk : key.pollEvents()){
                Path path = Paths.get(dir.toString(), wk.context().toString());
                WatchEvent.Kind<Path> kind = (WatchEvent.Kind<Path>) wk.kind();

                // Deal with folder creation
                if (isRecursive && (kind == ENTRY_CREATE)) {
                    Path child = dir.resolve(path);
                    if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS))
                        registerAll(child);
                }

                mEventReceivedDelegate.execute(kind, path);
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