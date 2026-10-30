package com.github.tvbox.osc.player.danmu;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.View;

import com.github.tvbox.osc.api.DanmakuApi;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.util.DanmuHelper;
import com.github.tvbox.osc.util.LOG;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.ui.widget.DanmakuView;
import master.flame.danmaku.danmaku.util.DanmakuUtils;
import xyz.doikki.videoplayer.player.VideoView;

public class DanmuLoadController {
    public interface LoadCallback {
        void onFailed();
    }

    private final MyVideoView videoView;
    private final DanmakuView danmuView;
    private final DanmakuContext danmakuContext;
    private final AtomicInteger loadSeq = new AtomicInteger();
    private ExecutorService executor;
    private String danmuText = "";
    private String danmuTitle = "";
    private String danmuEpisode = "";
    private int startedSeq = -1;
    private boolean pendingPrepare;
    private boolean temporarilyClosed;
    private LoadCallback loadCallback;
    private volatile int seekGeneration;
    private volatile long pendingSeekPosition = -1;

    public DanmuLoadController(MyVideoView videoView, DanmakuView danmuView) {
        this.videoView = videoView;
        this.danmuView = danmuView;
        this.danmakuContext = DanmakuContext.create();
        if (this.videoView != null) {
            this.videoView.setDanmuView(this.danmuView);
            this.videoView.setDanmuSeekListener(this::onDanmuSeek);
        }
        applySettings(false);
    }

    public void applySettings(boolean reload) {
        if (danmuView == null || danmakuContext == null) return;
        if (!DanmuHelper.isOpen()) {
            releaseView();
            return;
        }
        HashMap<Integer, Integer> maxLines = new HashMap<>();
        int maxLine = calculateMaxLine();
        maxLines.put(BaseDanmaku.TYPE_FIX_TOP, maxLine);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_RL, maxLine);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_LR, maxLine);
        maxLines.put(BaseDanmaku.TYPE_FIX_BOTTOM, maxLine);
        danmakuContext.setMaximumLines(maxLines)
                .setScrollSpeedFactor(DanmuHelper.getSpeed())
                .setDanmakuTransparency(DanmuHelper.getAlpha())
                .setScaleTextSize(DanmuHelper.getSizeScale());
        danmakuContext.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3)
                .setDanmakuMargin(8);
        float density = danmuView.getResources().getDisplayMetrics().density;
        danmuView.setPadding(0, Math.round(DanmuHelper.getTopMargin() * density),
                0, Math.round(DanmuHelper.getBottomMargin() * density));
        if (reload && !TextUtils.isEmpty(danmuText) && DanmuHelper.isOpen()) {
            prepare(danmuText);
        }
    }

    private int calculateMaxLine() {
        float density = danmuView == null ? 1f : danmuView.getResources().getDisplayMetrics().density;
        int height = danmuView == null ? 0 : danmuView.getHeight();
        if (height <= 0) height = Math.round(720 * density);
        int lineHeight = Math.max(1, Math.round(25f * DanmuHelper.getSizeScale() * density * 1.6f));
        int available = Math.round(height * DanmuHelper.getDisplayArea() / 100f);
        int lineCount = Math.max(1, available / lineHeight);
        lineCount = Math.round(lineCount * DanmuHelper.getDensity() / 100f);
        return Math.max(1, Math.min(lineCount, 20));
    }

    public void check(String danmu) {
        check(danmu, "", "");
    }

    public void check(String danmu, String title, String episode) {
        check(danmu, title, episode, null);
    }

    public void check(String danmu, String title, String episode, LoadCallback callback) {
        DanmakuApi.cancel();
        loadCallback = callback;
        seekGeneration++;
        pendingSeekPosition = -1;
        temporarilyClosed = false;
        danmuText = TextUtils.isEmpty(danmu) ? "" : danmu.trim();
        danmuTitle = TextUtils.isEmpty(title) ? "" : title;
        danmuEpisode = TextUtils.isEmpty(episode) ? "" : episode;
        releaseView();
        boolean hasDanmu = !TextUtils.isEmpty(danmuText);
        if (!hasDanmu || !DanmuHelper.isOpen()) {
            if (danmuView != null) danmuView.setVisibility(View.GONE);
            return;
        }
        if (danmuView != null) danmuView.setVisibility(View.VISIBLE);
        if (!isVideoReady()) {
            pendingPrepare = true;
            return;
        }
        prepare(danmuText);
    }

    public void startIfReady() {
        if (pendingPrepare && !TextUtils.isEmpty(danmuText) && DanmuHelper.isOpen()
                && videoView != null && isVideoReady()) {
            pendingPrepare = false;
            prepare(danmuText);
            return;
        }
        startIfReady(loadSeq.get());
    }

    public void reset() {
        DanmakuApi.cancel();
        seekGeneration++;
        pendingSeekPosition = -1;
        temporarilyClosed = false;
        danmuText = "";
        danmuTitle = "";
        danmuEpisode = "";
        pendingPrepare = false;
        loadCallback = null;
        loadSeq.incrementAndGet();
        startedSeq = -1;
        releaseView();
    }

    public void close() {
        DanmakuApi.cancel();
        loadSeq.incrementAndGet();
        seekGeneration++;
        pendingSeekPosition = -1;
        startedSeq = -1;
        pendingPrepare = false;
        releaseView();
    }

    public boolean toggle() {
        return setEnabled(!DanmuHelper.isOpen());
    }

    public boolean hasDanmaku() {
        return !TextUtils.isEmpty(danmuText);
    }

    public boolean setEnabled(boolean enabled) {
        DanmuHelper.setOpen(enabled);
        temporarilyClosed = !enabled;
        if (!enabled) {
            close();
            return false;
        }
        reloadForPlayback();
        startIfReady();
        return true;
    }

    public boolean addDanmaku(String text) {
        if (!DanmuHelper.isOpen() || TextUtils.isEmpty(text) || danmuView == null
                || danmakuContext == null || !danmuView.isPrepared()) {
            return false;
        }
        try {
            BaseDanmaku item = danmakuContext.mDanmakuFactory.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL, danmakuContext);
            if (item == null) return false;
            item.textSize = 25f * DanmuHelper.getSizeScale();
            item.textColor = DanmuHelper.useRandomColor() ? DanmuHelper.randomColor() : Color.WHITE;
            item.textShadowColor = Color.BLACK;
            item.flags = danmakuContext.mGlobalFlagValues;
            item.setTime(videoView == null ? 0 : Math.max(0, videoView.getCurrentPosition()));
            DanmakuUtils.fillText(item, text.trim());
            danmuView.addDanmaku(item);
            return true;
        } catch (Throwable th) {
            LOG.e("echo-danmu add local error: " + th.getMessage());
            return false;
        }
    }

    public void reloadForPlayback() {
        temporarilyClosed = false;
        loadSeq.incrementAndGet();
        seekGeneration++;
        pendingSeekPosition = -1;
        startedSeq = -1;
        releaseView();
        pendingPrepare = !TextUtils.isEmpty(danmuText) && DanmuHelper.isOpen();
    }

    public void destroy() {
        reset();
        if (videoView != null) videoView.setDanmuSeekListener(null);
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void prepare(String danmu) {
        if (TextUtils.isEmpty(danmu)) return;
        pendingPrepare = false;
        int seq = loadSeq.incrementAndGet();
        startedSeq = -1;
        final int currentSeekGeneration = seekGeneration;
        LOG.i("echo-danmu load title: " + safeLog(danmuTitle) + ", episode: " + safeLog(danmuEpisode) + ", source: " + getSourceSummary(danmu));
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor();
        }
        final long initialPosition;
        if (pendingSeekPosition >= 0) {
            initialPosition = pendingSeekPosition;
        } else if (videoView == null) {
            initialPosition = 0;
        } else {
            long currentPosition = videoView.getCurrentPosition();
            initialPosition = currentPosition > 0 ? currentPosition : videoView.getPlaybackPosition();
        }
        LOG.i("echo-danmu parse start at: " + initialPosition);
        executor.execute(() -> {
            Parser currentParser = new Parser(danmu, () -> seq != loadSeq.get(), initialPosition);
            if (seq != loadSeq.get()) return;
            int danmuCount = currentParser.getDanmuCount();
            LOG.i("echo-danmu parsed count: " + danmuCount);
            if (danmuView == null) return;
            final Parser preparedParser = currentParser;
            danmuView.post(() -> {
                if (seq != loadSeq.get() || danmakuContext == null) return;
                try {
                    danmuView.release();
                    if (videoView != null) videoView.setDanmuView(danmuView);
                    if (danmuCount <= 0) {
                        LOG.e("echo-danmu empty after parse");
                        danmuView.setVisibility(View.GONE);
                        notifyLoadFailed(seq);
                        return;
                    }
                    danmuView.prepare(preparedParser, danmakuContext);
                    clearLoadCallback(seq);
                    danmuView.setVisibility(DanmuHelper.isOpen() ? View.VISIBLE : View.GONE);
                    startIfReady(seq);
                    danmuView.postDelayed(() -> startIfReady(seq), 300);
                    danmuView.postDelayed(() -> startIfReady(seq), 1000);
                    appendRemaining(preparedParser, seq, currentSeekGeneration);
                } catch (Throwable th) {
                    LOG.e("echo-danmu prepare error: " + th.getMessage());
                    danmuView.setVisibility(View.GONE);
                    notifyLoadFailed(seq);
                }
            });
        });
    }

    private void appendRemaining(Parser parser, int seq, int generation) {
        appendRemaining(parser, seq, generation, false);
    }

    private void appendRemaining(Parser parser, int seq, int generation, boolean seekFirstBatch) {
        if (seq != loadSeq.get() || generation != seekGeneration || !DanmuHelper.isOpen() || !parser.hasMoreDanmaku()) return;
        if (danmuView == null) return;
        if (!danmuView.isPrepared()) {
            danmuView.postDelayed(() -> appendRemaining(parser, seq, generation, seekFirstBatch), 100);
            return;
        }
        if (executor == null || executor.isShutdown()) return;
        executor.execute(() -> {
            if (seq != loadSeq.get() || generation != seekGeneration || !DanmuHelper.isOpen() || danmuView == null) return;
            List<BaseDanmaku> batch = parser.parseNextBatch();
            if (batch.isEmpty()) return;
            for (BaseDanmaku item : batch) {
                if (seq != loadSeq.get() || generation != seekGeneration || !DanmuHelper.isOpen() || danmuView == null) return;
                danmuView.addDanmaku(item);
            }
            LOG.i("echo-danmu appended count: " + batch.size());
            if (seq == loadSeq.get() && generation == seekGeneration && DanmuHelper.isOpen() && parser.hasMoreDanmaku()) {
                long delay = seekFirstBatch ? 1000 : 50;
                danmuView.postDelayed(() -> appendRemaining(parser, seq, generation), delay);
            }
        });
    }

    private void onDanmuSeek(long position) {
        long targetPosition = Math.max(0, position);
        pendingSeekPosition = targetPosition;
        seekGeneration++;
        loadSeq.incrementAndGet();
        startedSeq = -1;
        if (DanmuHelper.isOpen() && !TextUtils.isEmpty(danmuText)) {
            pendingPrepare = true;
            if (danmuView != null) {
                danmuView.setVisibility(View.GONE);
                danmuView.postDelayed(this::startIfReady, 300);
            }
        }
    }

    private void clearLoadCallback(int seq) {
        if (seq == loadSeq.get()) loadCallback = null;
    }

    private void notifyLoadFailed(int seq) {
        if (seq != loadSeq.get() || loadCallback == null) return;
        LoadCallback callback = loadCallback;
        loadCallback = null;
        callback.onFailed();
    }

    private void startIfReady(int seq) {
        if (seq != loadSeq.get()
                || seq == startedSeq
                || videoView == null
                || !videoView.isPlaying()
                || danmuView == null
                || !danmuView.isPrepared()
                || !DanmuHelper.isOpen()) {
            return;
        }
        long position = videoView.getCurrentPosition();
        danmuView.setVisibility(View.VISIBLE);
        danmuView.seekTo(position);
        danmuView.start(position);
        startedSeq = seq;
        LOG.i("echo-danmu start at: " + position);
    }

    private boolean isVideoReady() {
        if (videoView == null) return false;
        int state = videoView.getCurrentPlayState();
        return state == VideoView.STATE_PREPARED
                || state == VideoView.STATE_BUFFERED
                || state == VideoView.STATE_PLAYING;
    }

    private void releaseView() {
        if (danmuView == null) return;
        try {
            danmuView.release();
        } catch (Throwable ignored) {
        }
        danmuView.setVisibility(View.GONE);
    }

    private String getSourceSummary(String danmu) {
        if (TextUtils.isEmpty(danmu)) return "";
        if (danmu.startsWith("http") || danmu.startsWith("file")) return danmu;
        return "inline xml length=" + danmu.length();
    }

    private String safeLog(String text) {
        return TextUtils.isEmpty(text) ? "" : text;
    }
}
