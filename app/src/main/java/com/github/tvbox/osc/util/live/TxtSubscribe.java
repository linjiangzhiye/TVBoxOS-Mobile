package com.github.tvbox.osc.util.live;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TxtSubscribe {
    private static final Pattern GROUP_TITLE = Pattern.compile("group-title=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern TVG_NAME = Pattern.compile("tvg-name=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    public static void parse(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> groups, String text) {
        if (text == null || text.trim().isEmpty()) return;
        if (text.contains("#EXTM3U") || text.contains("#EXTINF")) parseM3u(groups, text);
        else parseTxt(groups, text);
    }

    private static void parseTxt(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> groups, String text) {
        LinkedHashMap<String, ArrayList<String>> ungrouped = new LinkedHashMap<>();
        LinkedHashMap<String, ArrayList<String>> current = ungrouped;
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] split = line.split(",", 2);
                if (split.length < 2) continue;
                if (line.contains("#genre#")) {
                    String groupName = split[0].trim();
                    current = groups.get(groupName);
                    if (current == null) {
                        current = new LinkedHashMap<>();
                        groups.put(groupName, current);
                    }
                    continue;
                }
                String channelName = split[0].trim();
                if (channelName.isEmpty()) continue;
                for (String url : split[1].trim().split("#")) addUrl(current, channelName, url);
            }
        } catch (Throwable ignored) {
        }
        if (!ungrouped.isEmpty()) groups.put("未分组", ungrouped);
    }

    private static void parseM3u(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> groups, String text) {
        String pendingName = "";
        String pendingGroup = "未分组";
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#EXTINF")) {
                    pendingGroup = extract(GROUP_TITLE, line, "未分组");
                    pendingName = extract(TVG_NAME, line, "");
                    int comma = line.lastIndexOf(',');
                    if (comma >= 0 && comma + 1 < line.length()) {
                        String displayName = line.substring(comma + 1).trim();
                        if (!displayName.isEmpty()) pendingName = displayName;
                    }
                    if (pendingName.isEmpty()) pendingName = "未命名频道";
                    continue;
                }
                if (line.startsWith("#")) continue;
                if (pendingName.isEmpty() || !isLiveUrl(line)) continue;
                LinkedHashMap<String, ArrayList<String>> group = groups.get(pendingGroup);
                if (group == null) {
                    group = new LinkedHashMap<>();
                    groups.put(pendingGroup, group);
                }
                addUrl(group, pendingName, line);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String extract(Pattern pattern, String line, String fallback) {
        Matcher matcher = pattern.matcher(line);
        return matcher.find() && !matcher.group(1).trim().isEmpty() ? matcher.group(1).trim() : fallback;
    }

    private static void addUrl(LinkedHashMap<String, ArrayList<String>> group, String name, String url) {
        String value = url == null ? "" : url.trim();
        if (name.isEmpty() || !isLiveUrl(value)) return;
        ArrayList<String> sources = group.get(name);
        if (sources == null) {
            sources = new ArrayList<>();
            group.put(name, sources);
        }
        if (!sources.contains(value)) sources.add(value);
    }

    private static boolean isLiveUrl(String url) {
        return url.startsWith("http://") || url.startsWith("https://")
                || url.startsWith("rtp://") || url.startsWith("rtsp://")
                || url.startsWith("rtmp://") || url.startsWith("udp://");
    }

    public static JsonArray live2JsonArray(LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> linkedHashMap) {
        JsonArray result = new JsonArray();
        for (String groupName : linkedHashMap.keySet()) {
            LinkedHashMap<String, ArrayList<String>> channelMap = linkedHashMap.get(groupName);
            if (channelMap == null || channelMap.isEmpty()) continue;
            JsonArray channels = new JsonArray();
            for (String channelName : channelMap.keySet()) {
                ArrayList<String> urls = channelMap.get(channelName);
                if (urls == null || urls.isEmpty()) continue;
                JsonArray sourceArray = new JsonArray();
                for (String url : urls) sourceArray.add(url);
                JsonObject channel = new JsonObject();
                channel.addProperty("name", channelName);
                channel.add("urls", sourceArray);
                channels.add(channel);
            }
            if (channels.size() == 0) continue;
            JsonObject group = new JsonObject();
            group.addProperty("group", groupName);
            group.add("channels", channels);
            result.add(group);
        }
        return result;
    }
}
