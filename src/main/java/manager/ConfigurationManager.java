package manager;

import log.Log;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;

/**
 * Created by edgar on 7/10/15.
 */
public class ConfigurationManager {

    private static final String TAG = ConfigurationManager.class.getSimpleName();

    /**
     * All the valid options for the configuration
     */
    private static String[] CONFIGURATION_OPTIONS = {
            "host", "port", "username", "password", "rootDir"
    };

    /**
     * Class that handles the configuration file
     */
    private PropertiesConfiguration mConfiguration;

    private Runnable mOnReload;

    public void setOnConfigurationReload(Runnable runnable) {
        mOnReload = runnable;
    }

    /** Function that loads the configuration file
     *
     * @param filePath where the file is located.
     * @return true if the configuration was loaded, false otherwise.
     */
    public boolean load(String filePath){
        mConfiguration = new PropertiesConfiguration();
        File file = new File(filePath);
        try {
            if (!file.exists()) {
                createConfigurationFile(file);
                return false;
            }
            mConfiguration.load(filePath);
            if(!isValidConfigurationFile(mConfiguration))
                return false;
            setupFileWatcher();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
        Log.d(TAG, "Loaded '" + file + "'.");
        return true;
    }

    /**
     * Creates a file watcher for the configuration file
     */
    private void setupFileWatcher() {
        new Thread(() -> {
            final Path path = Paths.get(System.getProperty("user.dir"));
            try (final WatchService watchService = FileSystems.getDefault().newWatchService()) {
                path.register(watchService, ENTRY_CREATE);
                while (!Thread.interrupted()) {
                    final WatchKey wk = watchService.take();
                    for (WatchEvent<?> event : wk.pollEvents()) {
                        final Path changed = (Path) event.context();
                        if (changed.endsWith("test.txt")) {
                            mConfiguration.refresh();
                            Log.d(TAG, "Reloaded 'test.txt'.");
                            if(mOnReload != null)
                                mOnReload.run();
                        }
                    }
                    boolean valid = wk.reset();
                    if (!valid) {
                        Log.d(TAG, "Key has been unregistered");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }).start();
    }

    /** Function that validates the configuration file
     *
     * @param mConfiguration to be validated
     * @return false if doesn't have a property that is required or if property value is null
     */
    private boolean isValidConfigurationFile(PropertiesConfiguration mConfiguration) {
        boolean isValid = true;
        for(String property : CONFIGURATION_OPTIONS) {
            String value = mConfiguration.getString(property);
            if (value == null) {
                Log.e(TAG, "Missing property '" + property + "'.");
                isValid = false;
            } else if (value.isEmpty()) {
                Log.e(TAG, "Property '" + property + "' has no value");
                isValid = false;
            }
        }
        return isValid;
    }

    /** Function that creates a new empty configuration file, the file still needs to be populated
     *
     * @param file where the configuration will be stored
     * @throws IOException
     * @throws ConfigurationException
     */
    private void createConfigurationFile(File file) throws Exception {
        Log.e("Configuration file '" + file + "' was not found.");
        Log.e(TAG, "Creating a new one and exiting...");
        if(file.createNewFile()) {
            throw new Exception("Could not create file");
        }
        mConfiguration = new PropertiesConfiguration();
        mConfiguration.setHeader("Config file for Submission Watcher");
        for(String property : CONFIGURATION_OPTIONS)
            mConfiguration.addProperty(property, "");
        mConfiguration.save(file);
    }

    public String getHost() { return mConfiguration.getString(CONFIGURATION_OPTIONS[0]); }
    public int getPort() { return mConfiguration.getInt(CONFIGURATION_OPTIONS[1]); }
    public String getUsername() { return mConfiguration.getString(CONFIGURATION_OPTIONS[2]); }
    public String getPassword() { return mConfiguration.getString(CONFIGURATION_OPTIONS[3]); }
    public String getRootDir() { return mConfiguration.getString(CONFIGURATION_OPTIONS[4]); }

    // -----------------------
    // ------ Singleton ------
    // -----------------------

    /**
     * Our singleton instance
     */
    private static ConfigurationManager mInstance = new ConfigurationManager();

    /**
     * Returns a the singleton instance
     *
     * @return the instance.
     */
    public static ConfigurationManager getInstance() {
        return mInstance;
    }

    /**
     * Private because cannot be called
     */
    private ConfigurationManager() { /* Empty */}
}
