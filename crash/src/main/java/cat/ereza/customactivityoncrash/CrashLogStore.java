package cat.ereza.customactivityoncrash;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class CrashLogStore {
    private static final String DIR = "logs";
    private static final String FILE = "crash.log";
    private static final int MAX_BYTES = 256 * 1024;

    private CrashLogStore() {
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
