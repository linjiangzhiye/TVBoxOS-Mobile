package cat.ereza.customactivityoncrash;

import android.content.Context;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CrashLogStore {
    private static final String DIR = "logs";
    private static final String FILE = "crash.log";
    private static final int MAX_BYTES = 256 * 1024;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static Context appContext;

    private CrashLogStore() {
    }

    public static void install(Context context) {
        if (context == null || !INSTALLED.compareAndSet(false, true)) return;
        appContext = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                append(appContext, formatCrash(thread, throwable));
            } catch (Throwable ignored) {
            }
            if (previous != null) previous.uncaughtException(thread, throwable);
        });
    }

    private static String formatCrash(Thread thread, Throwable throwable) {
        StringWriter writer = new StringWriter();
        PrintWriter print = new PrintWriter(writer);
        print.println("Build version: " + (appContext == null ? "" : getVersionName(appContext)));
        print.println("Current date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date()));
        print.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
        print.println("OS version: Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        print.println("Thread: " + (thread == null ? "" : thread.getName()));
        print.println("Stack trace:");
        if (throwable != null) throwable.printStackTrace(print);
        print.flush();
        return writer.toString();
    }

    private static String getVersionName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static synchronized void append(Context context, String details) {
        if (context == null || details == null || details.trim().isEmpty()) return;
        try {
            File dir = new File(context.getFilesDir(), DIR);
            if (!dir.exists() && !dir.mkdirs()) return;
            File file = new File(dir, FILE);
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
            String block = "\n\n===== " + time + " =====\n" + details.trim() + "\n";
            byte[] data = block.getBytes(StandardCharsets.UTF_8);
            if (file.length() + data.length > MAX_BYTES) {
                byte[] old = readBytes(file);
                int keep = Math.min(old.length, MAX_BYTES / 2);
                try (FileOutputStream output = new FileOutputStream(file, false)) {
                    output.write(old, old.length - keep, keep);
                }
            }
            try (FileOutputStream output = new FileOutputStream(file, true)) {
                output.write(data);
            }
        } catch (Throwable ignored) {
        }
    }

    public static synchronized String read(Context context) {
        if (context == null) return "";
        try {
            File file = new File(new File(context.getFilesDir(), DIR), FILE);
            return new String(readBytes(file), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static synchronized void clear(Context context) {
        if (context == null) return;
        File file = new File(new File(context.getFilesDir(), DIR), FILE);
        if (file.exists()) file.delete();
    }

    private static byte[] readBytes(File file) {
        if (file == null || !file.exists()) return new byte[0];
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toByteArray();
        } catch (Throwable ignored) {
            return new byte[0];
        }
    }
}
