package manager;

import log.Log;
import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;

import java.io.*;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Class that deals with the upload requests
 *
 * @author edgar
 */
public class FTPManager implements Runnable {

    private static final String TAG = FTPManager.class.getSimpleName();

    /**
     * Instance of our FTPManager to be returned on  <code>{@link #getInstance()}
     */
    private static FTPManager mInstance;

    /**
     * Function to return a single instance of FTPManager
     *
     * @return The singleton instance of FTPManager
     */
    public static synchronized FTPManager getInstance() {
        if (mInstance == null)
            mInstance = new FTPManager();
        return mInstance;
    }

    // ------------------------
    // Class methods and fields
    // ------------------------

    /**
     * Queue where the files to be uploaded are stored
     */
    private Queue<File> mFilesToUpload;

    /**
     * Thread that deals with the file uploads
     */
    private Thread mFileUploadThread;

    private FTPSClient mFTPSClient;

    /**
     * Private Constructor for the Singleton design pattern
     */
    private FTPManager() {
        mFilesToUpload = new PriorityBlockingQueue<>();
        mFTPSClient = new FTPSClient("TLS");

        // Uncomment to print FTP outut
        //mFTPSClient.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out))); // outputs all conversation to the console

        try {
            File file = new File("filesQueue.dat");
            if (file.exists()) {
                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
                mFilesToUpload = (PriorityBlockingQueue) ois.readObject();
                ois.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (mFilesToUpload.size() > 0) {
            startThread();
        }

        //mFTPSClient.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out))); // outputs all conversation to the console
    }

    /**
     * Send file as soon as possible
     *
     * @param file The file to send
     */
    public void sendFile(File file) {
        // Add the file to the queue
        mFilesToUpload.add(file);

        // Write queue to file
        saveQueue();

        // Start thread to deal with file uploads
        startThread();
    }

    private void startThread() {
        if (mFileUploadThread == null || !mFileUploadThread.isAlive()) {
            // Create a new thread to deal with the upload
            mFileUploadThread = new Thread(this, TAG + " Thread");
            mFileUploadThread.start();
        }
    }

    private void saveQueue() {
        try {
            FileOutputStream fout = new FileOutputStream("filesQueue.dat");
            ObjectOutputStream oos = new ObjectOutputStream(fout);
            oos.writeObject(mFilesToUpload);
            oos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Runnable that uploads all files
     */
    public void run() {
        try {
            mFTPSClient.connect(ConfigurationManager.getInstance().getHost(), ConfigurationManager.getInstance().getPort());
            // Set protection buffer size
            mFTPSClient.execPBSZ(0);
            // Set data channel protection to private
            mFTPSClient.execPROT("P");

            // Check if the connection was successful
            int reply = mFTPSClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                mFTPSClient.disconnect();
                throw new Exception("Exception in connecting to FTP Server");
            }

            // Check if login was successful
            if (!mFTPSClient.login(ConfigurationManager.getInstance().getUsername(), ConfigurationManager.getInstance().getPassword())) {
                mFTPSClient.disconnect();
                throw new Exception("Could not login");
            }

//            mFTPSClient.setControlKeepAliveTimeout(1000);
            mFTPSClient.setFileType(mFTPSClient.BINARY_FILE_TYPE);
            mFTPSClient.changeWorkingDirectory(ConfigurationManager.getInstance().getRootDir());
            mFTPSClient.enterLocalPassiveMode();
            mFTPSClient.setControlKeepAliveTimeout(300); // 5min

            // Iterate over the queue and try to send all files
            while (!Thread.interrupted()) {
                // Get the first file, if does not have element throw NoSuchElementException
                File file = mFilesToUpload.element();

                // If the file exists send it
                if (file.exists()) {
                    InputStream inputStream = new FileInputStream(file);
                    Log.d(TAG, "Sending - " + file.getAbsolutePath());

                    if (!mFTPSClient.storeFile(file.getName(), inputStream))
                        throw new Exception("Could not store file \"" + file + "\".");

                }

                // Update the queue after sending
                mFilesToUpload.remove(file);
                saveQueue();
            }


        } catch (NoSuchElementException e) {
            Log.d(TAG, "Finished");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, mFilesToUpload.size() + " files to upload");
        } finally {
            disconnect();
        }
    }

    /**
     * Disconnects our FTPClient
     */
    private void disconnect() {
        try {
            mFTPSClient.disconnect();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }

}