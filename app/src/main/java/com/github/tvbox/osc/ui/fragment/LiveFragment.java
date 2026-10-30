package com.github.tvbox.osc.ui.fragment;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.ui.activity.LiveActivity;
import com.github.tvbox.osc.ui.activity.SubscriptionActivity;
import com.github.tvbox.osc.ui.adapter.LiveChannelGroupNewAdapter;
import com.github.tvbox.osc.ui.adapter.LiveChannelItemNewAdapter;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.live.LiveSourceRepository;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

public class LiveFragment extends BaseLazyFragment {
    private RecyclerView groupView;
    private RecyclerView channelView;
    private TextView emptyView;
    private LiveChannelGroupNewAdapter groupAdapter;
    private LiveChannelItemNewAdapter channelAdapter;
    private List<LiveChannelGroup> groups = new ArrayList<>();
    private String loadingSource = "";
    private int loadGeneration;

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_live;
    }

    @Override
    protected void init() {
        groupView = findViewById(R.id.rv_live_groups);
        channelView = findViewById(R.id.rv_live_channels);
        emptyView = findViewById(R.id.tv_live_empty);
        groupView.setLayoutManager(new LinearLayoutManager(mContext));
        channelView.setLayoutManager(new GridLayoutManager(mContext, 2));
        groupAdapter = new LiveChannelGroupNewAdapter();
        channelAdapter = new LiveChannelItemNewAdapter();
        groupView.setAdapter(groupAdapter);
        channelView.setAdapter(channelAdapter);

        groupAdapter.setOnItemClickListener((adapter, view, position) -> selectGroup(position, false));
        channelAdapter.setOnItemClickListener((adapter, view, position) -> playChannel(position));
        findViewById(R.id.tv_live_subscription).setOnClickListener(view -> {
            Intent intent = new Intent(mContext, SubscriptionActivity.class);
            intent.putExtra(SubscriptionActivity.EXTRA_LIVE, true);
            startActivity(intent);
        });
        refresh();
    }

    @Override
    protected void onFragmentResume() {
        super.onFragmentResume();
        refresh();
    }

    private void refresh() {
        String liveUrl = Hawk.get(HawkConfig.LIVE_URL, "");
        if (TextUtils.equals(loadingSource, liveUrl) && !groups.isEmpty()) return;
        loadingSource = liveUrl;
        final int generation = ++loadGeneration;
        showStatus("正在加载直播源...", false);
        LiveSourceRepository.load(liveUrl, new LiveSourceRepository.Callback0() {
            @Override
            public void onSuccess(List<LiveChannelGroup> result, boolean fromCache) {
                if (generation != loadGeneration) return;
                groups = result == null ? new ArrayList<>() : result;
                groupAdapter.setNewData(groups);
                if (groups.isEmpty()) {
                    channelAdapter.setNewData(new ArrayList<>());
                    showStatus("直播源中没有可用频道，请更换直播订阅", true);
                    return;
                }
                emptyView.setVisibility(View.GONE);
                selectGroup(0, false);
            }

            @Override
            public void onError(String message) {
                if (generation != loadGeneration) return;
                groups = new ArrayList<>();
                groupAdapter.setNewData(groups);
                channelAdapter.setNewData(new ArrayList<>());
                showStatus("直播源加载失败\n" + message + "\n可点击右上角“直播订阅”更换源", true);
            }
        });
    }

    private void showStatus(String message, boolean retryable) {
        emptyView.setText(message);
        emptyView.setVisibility(View.VISIBLE);
        emptyView.setOnClickListener(retryable ? view -> {
            LiveSourceRepository.invalidate(loadingSource);
            loadingSource = "";
            refresh();
        } : null);
    }

    private void selectGroup(int position, boolean openIfEmpty) {
        if (position < 0 || position >= groups.size()) return;
        groupAdapter.setSelectedGroupIndex(position);
        LiveChannelGroup group = groups.get(position);
        List<LiveChannelItem> channels = group.getLiveChannels() == null
                ? new ArrayList<>() : group.getLiveChannels();
        channelAdapter.setNewData(channels);
        emptyView.setVisibility(channels.isEmpty() ? View.VISIBLE : View.GONE);
        if (channels.isEmpty()) emptyView.setText("当前分组没有频道");
        if (channels.isEmpty() && openIfEmpty) openLive(position, -1);
    }

    private void playChannel(int position) {
        if (position < 0 || position >= channelAdapter.getData().size()) return;
        int group = groupAdapter.getSelectedGroupIndex();
        if (group < 0) group = 0;
        openLive(group, position);
    }

    private void openLive(int groupIndex, int channelIndex) {
        Intent intent = new Intent(mContext, LiveActivity.class);
        intent.putExtra(LiveActivity.EXTRA_GROUP_INDEX, groupIndex);
        intent.putExtra(LiveActivity.EXTRA_CHANNEL_INDEX, channelIndex);
        startActivity(intent);
    }
}
