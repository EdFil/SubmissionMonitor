package log;

/**
 * Created by edgar on 7/10/15.
 */
public class Log {

    private static final String DEFULT_LOG_TAG = Log.class.getSimpleName();

    //private static final boolean LOG_TO_FILE = false;

    public static void d(String message) {
        d(DEFULT_LOG_TAG, message);
    }

    public static void d(String tag, String message) {
        System.out.println(String.format("[%s] %s", tag, message));
    }

    public static void e(String message) {
        e(DEFULT_LOG_TAG, message);
    }

    public static void e(String tag, String message) {
        System.err.println(String.format("[%s] %s", tag, message));
    }

}
