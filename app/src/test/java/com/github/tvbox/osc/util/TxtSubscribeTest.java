package com.github.tvbox.osc.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.github.tvbox.osc.util.live.TxtSubscribe;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class TxtSubscribeTest {
    @Test
    public void duplicateUrlsAreDeduplicatedWithinChannelOnly() {
        LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> groups = new LinkedHashMap<>();
        TxtSubscribe.parse(groups, "新闻,#genre#\nA,https://example.com/live#https://example.com/live\n"
                + "A,https://example.com/live\nB,https://example.com/live\n");
        LinkedHashMap<String, ArrayList<String>> channels = groups.get("新闻");
        assertEquals(1, channels.get("A").size());
        assertEquals(1, channels.get("B").size());
    }

    @Test
    public void m3uMetadataIsPreservedForEpgAndLogo() {
        String m3u = "#EXTM3U\n"
                + "#EXTINF:-1 tvg-id=\"cctv1\" tvg-name=\"CCTV1\" tvg-logo=\"https://example.com/logo.png\" "
                + "tvg-url=\"https://example.com/epg.xml\" group-title=\"央视\",CCTV1\n"
                + "https://example.com/live.m3u8\n";
        JsonArray groups = TxtSubscribe.parseToJsonArray(m3u);
        assertNotNull(groups);
        JsonObject channel = groups.get(0).getAsJsonObject()
                .getAsJsonArray("channels").get(0).getAsJsonObject();
        assertEquals("cctv1", channel.get("tvg-id").getAsString());
        assertEquals("CCTV1", channel.get("tvg-name").getAsString());
        assertEquals("https://example.com/logo.png", channel.get("logo").getAsString());
        assertEquals("https://example.com/epg.xml", channel.get("epg").getAsString());
    }
}
