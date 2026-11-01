package com.undcover.freedom.pyramid;

import android.app.Application;
import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.github.catvod.Proxy;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.tvbox.osc.util.MD5;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Chaquopy bridge used only by the mobile spider module. */
public final class PythonLoader {
    private static final long INIT_TIMEOUT_SECONDS = 30L;
    private static final ExecutorService INIT_EXECUTOR = Executors.newCachedThreadPool();
    private static volatile PythonLoader instance;

    private final ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private final HashMap<String, JSONObject> siteMap = new HashMap<>();
    private volatile Application application;
    private volatile String cachePath = "";
    private volatile String configText = "";
    private volatile Python python;
    volatile PyObject pyApp;

    private PythonLoader() {
    }

    public static PythonLoader getInstance() {
        if (instance == null) {
            synchronized (PythonLoader.class) {
                if (instance == null) instance = new PythonLoader();
            }
        }
        return instance;
    }

    public synchronized void ensureStarted(Context context) {
        if (context == null || python != null) return;
        Application app = (Application) context.getApplicationContext();
        application = app;
        PyToast.init(app);
        PyLog.getInstance().setLogLevel(PyLog.LEVEL_RELEASE).setFilter(0);
        PyLog.TagConstant.TAG_APP = "MBoxPython";
        if (!Python.isStarted()) Python.start(new AndroidPlatform(app));
        python = Python.getInstance();
        pyApp = python.getModule("app");
        File pyCache = new File(app.getCacheDir(), "pyplugin");
        if (!pyCache.exists()) pyCache.mkdirs();
        cachePath = pyCache.getAbsolutePath();
        if (!configText.isEmpty()) applyConfig(configText);
    }

    public synchronized void clear() {
        spiders.clear();
    }

    public void setConfig(String json) {
        if (json == null || json.isEmpty() || json.equals(configText)) return;
        configText = json;
        if (python != null) applyConfig(json);
    }

    private void applyConfig(String json) {
        try {
            JSONObject config = new JSONObject(json);
            JSONArray sites = config.optJSONArray("sites");
            siteMap.clear();
            spiders.clear();
            if (sites == null) return;
            for (int i = 0; i < sites.length(); i++) {
                JSONObject site = sites.optJSONObject(i);
                if (site == null) continue;
                String api = site.optString("api", "").trim();
                if (!api.isEmpty()) siteMap.put(api, site);
            }
        } catch (Throwable ignored) {
        }
    }

    public String getUrlByApi(String api) {
        JSONObject site = siteMap.get(api);
        if (site == null || !site.has("ext") || site.isNull("ext")) return "";
        return site.optString("ext", "").trim();
    }

    public Spider getSpider(String key, String url, String ext) {
        Spider cached = spiders.get(key);
        if (cached != null) return cached;
        if (application == null) return new SpiderNull();
        Future<Spider> future = INIT_EXECUTOR.submit(() -> {
            String cache = new File(cachePath, MD5.string2MD5(key)).getAbsolutePath();
            PythonSpider spider = new PythonSpider(key, cache);
            spider.init(application, appendExt(url, ext));
            return spider.isLoadSuccess() ? spider : new SpiderNull();
        });
        try {
            Spider spider = future.get(INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (spider instanceof PythonSpider) spiders.put(key, spider);
            return spider;
        } catch (Throwable th) {
            future.cancel(true);
            PyLog.e("Python init failed: " + key + " " + th.getMessage());
            return new SpiderNull();
        }
    }

    private String appendExt(String url, String ext) {
        if (ext == null || ext.trim().isEmpty()) return url;
        return url + (url.contains("?") ? "&" : "?") + "extend=" + android.net.Uri.encode(ext.trim());
    }

    public String localProxyUrl() {
        return Proxy.getUrl() + "proxy";
    }
}
