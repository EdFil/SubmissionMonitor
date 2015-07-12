package manager;

import log.Log;
import org.apache.commons.net.ftp.FTP;
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
        return mInstance;
    }

    /**
     * Initializes the FTPManager Singleton
     */
    public static void init() {
        mInstance = new FTPManager();
        mInstance.startThread();
    }

    // ------------------------
    // Class methods and fields
    // ------------------------

    /**
     * Queue where the files to be uploaded are stored
     */
    private Queue<FileToSendInfo> mFilesToUpload;

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
                mFilesToUpload = (Queue<FileToSendInfo>) ois.readObject();
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
    public void sendFile(File file, String remote) {
        // Add the file to the queue
        mFilesToUpload.add(new FileToSendInfo(file, remote));

        // Write queue to file
        saveQueue();

        // Start thread to deal with file uploads
        startThread();
    }

    public void startThread() {
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
            Log.d(TAG, "Starting");
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
            mFTPSClient.setFileType(FTP.BINARY_FILE_TYPE);
            mFTPSClient.enterLocalPassiveMode();
            mFTPSClient.setControlKeepAliveTimeout(300); // 5min

            // Iterate over the queue and try to send all files
            while (!Thread.interrupted()) {
                // Get the first file, if does not have element throw NoSuchElementException
                FileToSendInfo fileToSendInfo = mFilesToUpload.element();

                File file = fileToSendInfo.getFile();
                // If the file exists send it
                if (file.exists()) {

                    if(!checkAndCreateRemoteDirectories(fileToSendInfo.mRelativePath)) {
                        throw new Exception("Could not access or create folders for \"" + file + "\".");
                    }

                    InputStream inputStream = new FileInputStream(file);
                    Log.d(TAG, "Sending - " + file.getAbsolutePath());

                    if (!mFTPSClient.storeFile(fileToSendInfo.mRelativePath, inputStream))
                        throw new Exception("Could not store file \"" + file + "\".");

                }

                // Update the queue after sending
                mFilesToUpload.remove(fileToSendInfo);
                saveQueue();
            }


        } catch (NoSuchElementException ignored) {

        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            Log.e(TAG, mFilesToUpload.size() + " files to upload");
        } finally {
            disconnect();
            Log.d(TAG, "Finished");
        }
    }

    /**
     * Function that checks and creates all the necessary folders for the remote path given
     *
     * @param remotePath where the file is going to be stored
     * @return true if it remote dir exists/was created, false otherwise
     * @throws IOException
     */
    private boolean checkAndCreateRemoteDirectories(String remotePath) throws IOException {
        String[] pathTokens = remotePath.split("/");
        String currentDir = ConfigurationManager.getInstance().getRootDir();

        // Change to root dir
        if(!mFTPSClient.changeWorkingDirectory(currentDir)) {
            Log.e(TAG, "Could not change to remote root dir");
            return false;
        }

        // For loop cycles every sub path of the file path.
        for(int i = 0; i < pathTokens.length - 1; i++) {

            // If the folder does not exist..
            if(!mFTPSClient.changeWorkingDirectory(pathTokens[i])){
                currentDir += "/" + pathTokens[i];
                Log.d(TAG, "Creating directory '" + currentDir + "'.");

                // Create the folder
                if(!mFTPSClient.makeDirectory(pathTokens[i])) {
                    Log.e(TAG, "Could not make dir '" + currentDir + "'.");
                    return false;
                }

                // Enter the newly created folder
                if(!mFTPSClient.changeWorkingDirectory(pathTokens[i])) {
                    Log.e(TAG, "Could not change to newly created dir '" + currentDir + "'.");
                    return false;
                }
            }
        }

        // Change back to root folder
        if(!mFTPSClient.changeWorkingDirectory(ConfigurationManager.getInstance().getRootDir())) {
            Log.d(TAG, "Could not change back to root dir");
            return false;
        }

        return true;
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

    /**
     * Wrapper class to have the file to send associated with a remote destination path
     */
    private class FileToSendInfo implements Comparable<FileToSendInfo>, Serializable {

        private static final long serialVersionUID = 120720151140L;

        private File mFile;
        private String mRelativePath;
        private long mDateAddedToQueue;

        public FileToSendInfo(File file, String relativePath) {
            mFile = file;
            mRelativePath = relativePath;
            mDateAddedToQueue = System.currentTimeMillis();
        }

        @Override
        public int compareTo(FileToSendInfo other) {
            return Long.compare(mDateAddedToQueue, other.mDateAddedToQueue);
        }

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.writeObject(mFile);
            out.writeObject(mRelativePath);
            out.writeLong(mDateAddedToQueue);
        }
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            mFile = (File)in.readObject();
            mRelativePath = (String)in.readObject();
            mDateAddedToQueue = in.readLong();
        }

        public File getFile() { return mFile; }
        public String getRelativePath() { return mRelativePath; }

    }

}