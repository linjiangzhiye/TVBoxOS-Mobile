package com.github.tvbox.osc.ui.fragment;

import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.ui.activity.LiveActivity;
import com.github.tvbox.osc.ui.activity.SubscriptionActivity;
import com.github.tvbox.osc.ui.adapter.LiveChannelGroupNewAdapter;
import com.github.tvbox.osc.ui.adapter.LiveChannelItemNewAdapter;
import com.github.tvbox.osc.util.HawkConfig;
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

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_live;
    }

    @Override
    protected void init() {
        groupView = findViewById(R.id.rv_live_groups);
        channelView = findViewById(R.id.rv_live_channels);
        emptyView = findViewById(R.id.tv_live_empty);
        groupView.setLayoutManager(new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false));
        channelView.setLayoutManager(new LinearLayoutManager(mContext));
        groupAdapter = new LiveChannelGroupNewAdapter();
        channelAdapter = new LiveChannelItemNewAdapter();
        groupView.setAdapter(groupAdapter);
        channelView.setAdapter(channelAdapter);

        groupAdapter.setOnItemClickListener((adapter, view, position) -> selectGroup(position, true));
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
        groups = new ArrayList<>();
        if (!TextUtils.isEmpty(liveUrl)) {
            LiveChannelGroup placeholder = new LiveChannelGroup();
            placeholder.setGroupIndex(0);
            placeholder.setGroupName("当前直播源");
            placeholder.setLiveChannels(new ArrayList<>());
            groups.add(placeholder);
        } else {
            groups.addAll(ApiConfig.get().getChannelGroupList());
        }
        groupAdapter.setNewData(groups);
        if (groups.isEmpty()) {
            channelAdapter.setNewData(new ArrayList<>());
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        emptyView.setVisibility(View.GONE);
        selectGroup(0, false);
    }

    private void selectGroup(int position, boolean openIfEmpty) {
        if (position < 0 || position >= groups.size()) return;
        groupAdapter.setSelectedGroupIndex(position);
        LiveChannelGroup group = groups.get(position);
        List<LiveChannelItem> channels = group.getLiveChannels() == null
                ? new ArrayList<>() : group.getLiveChannels();
        channelAdapter.setNewData(channels);
        if (channels.isEmpty() && openIfEmpty) {
            openLive(position, -1);
        }
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
