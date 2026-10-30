package com.github.tvbox.osc.util.live;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.github.catvod.net.OkHttp;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.google.gson.JsonArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;

/** Loads TXT/M3U live subscriptions off the UI thread and keeps a short-lived cache. */
public final class LiveSourceRepository {
    public interface Callback0 {
        void onSuccess(List<LiveChannelGroup> groups, boolean fromCache);

        void onError(String message);
    }

    private static final long CACHE_TTL = 10 * 60 * 1000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, CachedGroups> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<Callback0>> WAITERS = new ConcurrentHashMap<>();

    private LiveSourceRepository() {
    }

    public static void load(String input, Callback0 callback) {
        if (callback == null) return;
        if (TextUtils.isEmpty(input)) {
            List<LiveChannelGroup> groups = new ArrayList<>(ApiConfig.get().getChannelGroupList());
            callback.onSuccess(groups, true);
            return;
        }

        final String key = input.trim();
        CachedGroups cached = CACHE.get(key);
        if (cached != null && cached.isFresh()) {
            callback.onSuccess(cached.groups, true);
            return;
        }

        List<Callback0> callbacks = WAITERS.get(key);
        if (callbacks != null) {
            callbacks.add(callback);
            return;
        }
        callbacks = new CopyOnWriteArrayList<>();
        callbacks.add(callback);
        WAITERS.put(key, callbacks);

        final String source = resolveSource(key);
        if (TextUtils.isEmpty(source)) {
            finishError(key, "直播源地址无效");
            return;
        }
        if (isHttp(source)) {
            loadRemote(key, source);
        } else {
            loadLocal(key, source);
        }
    }

    public static void invalidate(String input) {
        if (TextUtils.isEmpty(input)) {
            CACHE.clear();
            return;
        }
        CACHE.remove(input.trim());
    }

    private static void loadRemote(final String key, String url) {
        try {
            Request request = new Request.Builder().url(url).build();
            OkHttp.client().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    finishError(key, TextUtils.isEmpty(e.getMessage()) ? "直播源加载失败" : e.getMessage());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
                        if (response.body() == null) throw new IOException("直播源内容为空");
                        finishSuccess(key, parse(response.body().string()));
                    } catch (Throwable th) {
                        finishError(key, TextUtils.isEmpty(th.getMessage()) ? "直播源解析失败" : th.getMessage());
                    } finally {
                        response.close();
                    }
                }
            });
        } catch (Throwable th) {
            finishError(key, TextUtils.isEmpty(th.getMessage()) ? "直播源加载失败" : th.getMessage());
        }
    }

    private static void loadLocal(final String key, final String path) {
        new Thread(() -> {
            try {
                File file = new File(path);
                if (!file.isFile()) throw new IOException("找不到本地直播源文件");
                finishSuccess(key, parse(readUtf8(file)));
            } catch (Throwable th) {
                finishError(key, TextUtils.isEmpty(th.getMessage()) ? "本地直播源解析失败" : th.getMessage());
            }
        }, "live-source-loader").start();
    }

    private static String resolveSource(String input) {
        String source = input == null ? "" : input.trim();
        if (source.startsWith("clan://localhost")) {
            return source.substring("clan://localhost".length());
        }
        if (source.startsWith("file://")) {
            try {
                return Uri.parse(source).getPath();
            } catch (Throwable ignored) {
                return "";
            }
        }
        if (source.startsWith("http://127.0.0.1")) {
            try {
                String ext = Uri.parse(source).getQueryParameter("ext");
                if (!TextUtils.isEmpty(ext)) {
                    return new String(Base64.decode(ext, Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), StandardCharsets.UTF_8);
                }
            } catch (Throwable ignored) {
            }
        }
        return source;
    }

    private static boolean isHttp(String source) {
        return source.startsWith("http://") || source.startsWith("https://");
    }

    private static List<LiveChannelGroup> parse(String text) {
        LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap = new LinkedHashMap<>();
        TxtSubscribe.parse(linkedHashMap, text);
        JsonArray array = TxtSubscribe.live2JsonArray(linkedHashMap);
        List<LiveChannelGroup> groups = ApiConfig.parseLives(array);
        if (groups.isEmpty()) throw new IllegalStateException("直播源中没有可用频道");
        return groups;
    }

    private static String readUtf8(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int read = input.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            return new String(data, 0, offset, StandardCharsets.UTF_8);
        }
    }

    private static void finishSuccess(String key, List<LiveChannelGroup> groups) {
        CACHE.put(key, new CachedGroups(groups));
        List<Callback0> callbacks = WAITERS.remove(key);
        if (callbacks == null) return;
        MAIN.post(() -> {
            for (Callback0 callback : callbacks) callback.onSuccess(groups, false);
        });
    }

    private static void finishError(String key, String message) {
        List<Callback0> callbacks = WAITERS.remove(key);
        if (callbacks == null) return;
        MAIN.post(() -> {
            for (Callback0 callback : callbacks) callback.onError(message);
        });
    }

    private static final class CachedGroups {
        final List<LiveChannelGroup> groups;
        final long loadedAt = System.currentTimeMillis();

        CachedGroups(List<LiveChannelGroup> groups) {
            this.groups = groups;
        }

        boolean isFresh() {
            return System.currentTimeMillis() - loadedAt < CACHE_TTL;
        }
    }
}
