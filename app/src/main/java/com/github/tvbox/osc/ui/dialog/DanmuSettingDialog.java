package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.DanmuHelper;
import com.lxj.xpopup.core.CenterPopupView;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class DanmuSettingDialog extends CenterPopupView {
    public interface Listener {
        void onDanmakuEnabledChanged(boolean enabled);

        void onDanmakuSettingsChanged();

        void onPickLocalFile();

        void onOpenSourceList();
    }

    private final Listener listener;
    private SeekBar areaView;
    private SeekBar densityView;
    private SeekBar topMarginView;
    private SeekBar bottomMarginView;
    private SeekBar opacityView;
    private SeekBar sizeView;
    private SeekBar speedView;
    private TextView areaLabel;
    private TextView densityLabel;
    private TextView topMarginLabel;
    private TextView bottomMarginLabel;
    private TextView opacityLabel;
    private TextView sizeLabel;
    private TextView speedLabel;

    public DanmuSettingDialog(@NonNull @NotNull Context context, Listener listener) {
        super(context);
        this.listener = listener;
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_danmu_setting;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        SwitchCompat openView = findViewById(R.id.danmu_open);
        SwitchCompat scrollView = findViewById(R.id.danmu_show_scroll);
        SwitchCompat topView = findViewById(R.id.danmu_show_top);
        SwitchCompat bottomView = findViewById(R.id.danmu_show_bottom);
        SwitchCompat randomView = findViewById(R.id.danmu_random_color);
        areaView = findViewById(R.id.danmu_area);
        densityView = findViewById(R.id.danmu_density);
        topMarginView = findViewById(R.id.danmu_top_margin);
        bottomMarginView = findViewById(R.id.danmu_bottom_margin);
        opacityView = findViewById(R.id.danmu_opacity);
        sizeView = findViewById(R.id.danmu_size);
        speedView = findViewById(R.id.danmu_speed);
        areaLabel = findViewById(R.id.danmu_area_label);
        densityLabel = findViewById(R.id.danmu_density_label);
        topMarginLabel = findViewById(R.id.danmu_top_margin_label);
        bottomMarginLabel = findViewById(R.id.danmu_bottom_margin_label);
        opacityLabel = findViewById(R.id.danmu_opacity_label);
        sizeLabel = findViewById(R.id.danmu_size_label);
        speedLabel = findViewById(R.id.danmu_speed_label);

        openView.setChecked(DanmuHelper.isOpen());
        scrollView.setChecked(DanmuHelper.showScroll());
        topView.setChecked(DanmuHelper.showTop());
        bottomView.setChecked(DanmuHelper.showBottom());
        randomView.setChecked(DanmuHelper.useRandomColor());
        areaView.setProgress(clamp(DanmuHelper.getDisplayArea() - 10, 0, areaView.getMax()));
        densityView.setProgress(clamp(DanmuHelper.getDensity() - 10, 0, densityView.getMax()));
        topMarginView.setProgress(clamp(DanmuHelper.getTopMargin(), 0, topMarginView.getMax()));
        bottomMarginView.setProgress(clamp(DanmuHelper.getBottomMargin(), 0, bottomMarginView.getMax()));
        opacityView.setProgress(clamp(Math.round(DanmuHelper.getAlpha() * 100f) - 10, 0, opacityView.getMax()));
        sizeView.setProgress(clamp(Math.round(DanmuHelper.getSizeScale() * 10f) - 6, 0, sizeView.getMax()));
        speedView.setProgress(clamp(Math.round(DanmuHelper.getSpeed() * 100f) - 50, 0, speedView.getMax()));
        updateLabels();

        openView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            DanmuHelper.setOpen(isChecked);
            if (listener != null) listener.onDanmakuEnabledChanged(isChecked);
        });
        scrollView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            DanmuHelper.setShowScroll(isChecked);
            applySettings();
        });
        topView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            DanmuHelper.setShowTop(isChecked);
            applySettings();
        });
        bottomView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            DanmuHelper.setShowBottom(isChecked);
            applySettings();
        });
        randomView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            DanmuHelper.setRandomColor(isChecked);
            applySettings();
        });
        bindSeekBar(areaView);
        bindSeekBar(densityView);
        bindSeekBar(topMarginView);
        bindSeekBar(bottomMarginView);
        bindSeekBar(opacityView);
        bindSeekBar(sizeView);
        bindSeekBar(speedView);

        findViewById(R.id.danmu_close).setOnClickListener(view -> dismiss());
        findViewById(R.id.danmu_local_file).setOnClickListener(view -> {
            dismiss();
            if (listener != null) listener.onPickLocalFile();
        });
        findViewById(R.id.danmu_source_list).setOnClickListener(view -> {
            dismiss();
            if (listener != null) listener.onOpenSourceList();
        });
    }

    private void bindSeekBar(SeekBar seekBar) {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateLabels();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                applySettings();
            }
        });
    }

    private void updateLabels() {
        areaLabel.setText(String.format(Locale.CHINA, "显示区域：%d%%", areaView.getProgress() + 10));
        densityLabel.setText(String.format(Locale.CHINA, "弹幕密度：%d%%", densityView.getProgress() + 10));
        topMarginLabel.setText(String.format(Locale.CHINA, "顶部边距：%d", topMarginView.getProgress()));
        bottomMarginLabel.setText(String.format(Locale.CHINA, "底部边距：%d", bottomMarginView.getProgress()));
        opacityLabel.setText(String.format(Locale.CHINA, "不透明度：%d%%", opacityView.getProgress() + 10));
        sizeLabel.setText(String.format(Locale.CHINA, "字体大小：%d%%", (sizeView.getProgress() + 6) * 10));
        speedLabel.setText(String.format(Locale.CHINA, "滚动速度：%d%%", speedView.getProgress() + 50));
    }

    private void applySettings() {
        DanmuHelper.setDisplayArea(areaView.getProgress() + 10);
        DanmuHelper.setDensity(densityView.getProgress() + 10);
        DanmuHelper.setTopMargin(topMarginView.getProgress());
        DanmuHelper.setBottomMargin(bottomMarginView.getProgress());
        DanmuHelper.setAlpha((opacityView.getProgress() + 10) / 100f);
        DanmuHelper.setSizeScale((sizeView.getProgress() + 6) / 10f);
        DanmuHelper.setSpeed((speedView.getProgress() + 50) / 100f);
        if (listener != null) listener.onDanmakuSettingsChanged();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
