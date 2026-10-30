package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.DanmakuApi;
import com.github.tvbox.osc.bean.DanmuSearchResult;
import com.lxj.xpopup.core.CenterPopupView;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DanmuSearchDialog extends CenterPopupView {
    public interface Listener {
        void onSelected(String danmu);
    }

    private final String title;
    private final String episode;
    private final Listener listener;
    private TextView statusView;
    private BaseQuickAdapter<DanmuSearchResult, BaseViewHolder> adapter;

    public DanmuSearchDialog(@NonNull @NotNull Context context, String title, String episode, Listener listener) {
        super(context);
        this.title = title;
        this.episode = episode;
        this.listener = listener;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_danmu_search;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        statusView = findViewById(R.id.danmu_search_status);
        RecyclerView listView = findViewById(R.id.danmu_search_list);
        listView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new BaseQuickAdapter<DanmuSearchResult, BaseViewHolder>(R.layout.item_danmu_search, new ArrayList<>()) {
            @Override
            protected void convert(BaseViewHolder helper, DanmuSearchResult item) {
                helper.setText(R.id.tv_name, item.getName());
                helper.setText(R.id.tv_url, item.getUrl());
            }
        };
        adapter.setOnItemClickListener((adapter, view, position) -> load((DanmuSearchResult) adapter.getItem(position)));
        listView.setAdapter(adapter);
        findViewById(R.id.danmu_search_close).setOnClickListener(view -> dismiss());
        search();
    }

    private void search() {
        if (TextUtils.isEmpty(title)) {
            statusView.setText("影片名称缺失");
            return;
        }
        statusView.setText("正在搜索...");
        DanmakuApi.searchList(title, episode, new DanmakuApi.SearchListCallback() {
            @Override
            public void onSuccess(List<DanmuSearchResult> results) {
                adapter.setNewData(results);
                if (results == null || results.isEmpty()) {
                    statusView.setText("未找到可用弹幕源，可在配置中设置自定义弹幕接口");
                } else {
                    statusView.setText("共找到 " + results.size() + " 个结果，点击选择");
                }
            }

            @Override
            public void onError(String message) {
                statusView.setText("搜索失败：" + message);
            }
        });
    }

    private void load(DanmuSearchResult result) {
        if (result == null) return;
        statusView.setText("正在加载弹幕...");
        DanmakuApi.loadSearchResult(result, new DanmakuApi.SearchResultCallback() {
            @Override
            public void onSuccess(String danmu) {
                if (listener != null) listener.onSelected(danmu);
                dismiss();
            }

            @Override
            public void onError(String message) {
                statusView.setText("加载失败：" + message);
                Toast.makeText(getContext(), "弹幕加载失败", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
