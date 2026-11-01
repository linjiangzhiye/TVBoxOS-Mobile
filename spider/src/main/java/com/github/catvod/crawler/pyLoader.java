package com.github.catvod.crawler;

import android.content.Context;

import com.github.tvbox.osc.util.MD5;
import com.undcover.freedom.pyramid.PythonLoader;
import com.undcover.freedom.pyramid.PythonSpider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Routes .py sites to the bundled Chaquopy spider runtime. */
public final class pyLoader {
    private final ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private volatile Context context;
    private volatile String recentPyKey = "";

    public void setContext(Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    public void setConfig(String json) {
        PythonLoader.getInstance().setConfig(json);
    }

    public void setRecentPyKey(String key) {
        recentPyKey = key == null ? "" : key;
    }

    public Spider getSpider(String key, String api, String ext) {
        Spider cached = spiders.get(key);
        if (cached != null) return cached;
        Context ctx = context;
        if (ctx == null) return new SpiderNull();
        try {
            PythonLoader loader = PythonLoader.getInstance();
            loader.ensureStarted(ctx);
            Spider spider = loader.getSpider(key, api, ext);
            if (!(spider instanceof SpiderNull)) spiders.put(key, spider);
            return spider;
        } catch (Throwable th) {
            return new SpiderNull();
        }
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        String key = recentPyKey;
        if (key == null || key.isEmpty()) return null;
        Spider spider = spiders.get(key);
        if (!(spider instanceof PythonSpider)) return null;
        try {
            return ((PythonSpider) spider).proxyLocal(params);
        } catch (Throwable th) {
            return null;
        }
    }

    public void clear() {
        spiders.clear();
        PythonLoader.getInstance().clear();
    }
}
