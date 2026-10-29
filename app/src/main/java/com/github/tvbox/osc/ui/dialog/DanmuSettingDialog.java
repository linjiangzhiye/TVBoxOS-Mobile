package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

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

        boolean onSendDanmaku(String text);
    }

    private final Listener listener;

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
        SwitchCompat randomView = findViewById(R.id.danmu_random_color);
        SeekBar heightView = findViewById(R.id.danmu_height);
        SeekBar opacityView = findViewById(R.id.danmu_opacity);
        SeekBar sizeView = findViewById(R.id.danmu_size);
        SeekBar speedView = findViewById(R.id.danmu_speed);
        TextView heightLabel = findViewById(R.id.danmu_height_label);
        TextView opacityLabel = findViewById(R.id.danmu_opacity_label);
        TextView sizeLabel = findViewById(R.id.danmu_size_label);
        TextView speedLabel = findViewById(R.id.danmu_speed_label);
        EditText textView = findViewById(R.id.danmu_text);

        openView.setChecked(DanmuHelper.isOpen());
        randomView.setChecked(DanmuHelper.useRandomColor());
        heightView.setProgress(clamp(DanmuHelper.getMaxLine() - 1, 0, heightView.getMax()));
        opacityView.setProgress(clamp(Math.round(DanmuHelper.getAlpha() * 100f) - 10, 0, opacityView.getMax()));
        sizeView.setProgress(clamp(Math.round(DanmuHelper.getSizeScale() * 10f) - 6, 0, sizeView.getMax()));
        speedView.setProgress(clamp(Math.round(DanmuHelper.getSpeed() * 100f) - 50, 0, speedView.getMax()));
        updateLabels(heightLabel, opacityLabel, sizeLabel, speedLabel, heightView, opacityView, sizeView, speedView);

        openView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            DanmuHelper.setOpen(isChecked);
            if (listener != null) listener.onDanmakuEnabledChanged(isChecked);
        });
        randomView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            DanmuHelper.setRandomColor(isChecked);
            applySettings();
        });
        bindSeekBar(heightView, heightLabel, opacityLabel, sizeLabel, speedLabel, heightView, opacityView, sizeView, speedView);
        bindSeekBar(opacityView, heightLabel, opacityLabel, sizeLabel, speedLabel, heightView, opacityView, sizeView, speedView);
        bindSeekBar(sizeView, heightLabel, opacityLabel, sizeLabel, speedLabel, heightView, opacityView, sizeView, speedView);
        bindSeekBar(speedView, heightLabel, opacityLabel, sizeLabel, speedLabel, heightView, opacityView, sizeView, speedView);

        findViewById(R.id.danmu_close).setOnClickListener(view -> dismiss());
        findViewById(R.id.danmu_send).setOnClickListener(view -> {
            String text = textView.getText() == null ? "" : textView.getText().toString().trim();
            if (TextUtils.isEmpty(text)) {
                Toast.makeText(getContext(), "请输入弹幕内容", Toast.LENGTH_SHORT).show();
                return;
            }
            if (listener != null && listener.onSendDanmaku(text)) {
                textView.setText("");
                Toast.makeText(getContext(), "弹幕已发送", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "请先开启弹幕并等待视频加载", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindSeekBar(SeekBar seekBar, TextView heightLabel, TextView opacityLabel,
                             TextView sizeLabel, TextView speedLabel, SeekBar heightView,
                             SeekBar opacityView, SeekBar sizeView, SeekBar speedView) {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateLabels(heightLabel, opacityLabel, sizeLabel, speedLabel,
                        heightView, opacityView, sizeView, speedView);
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

    private void updateLabels(TextView heightLabel, TextView opacityLabel, TextView sizeLabel,
                              TextView speedLabel, SeekBar heightView, SeekBar opacityView,
                              SeekBar sizeView, SeekBar speedView) {
        heightLabel.setText(String.format(Locale.CHINA, "上下高度：%d 行", heightView.getProgress() + 1));
        opacityLabel.setText(String.format(Locale.CHINA, "不透明度：%d%%", opacityView.getProgress() + 10));
        sizeLabel.setText(String.format(Locale.CHINA, "字体大小：%.1fx", (sizeView.getProgress() + 6) / 10f));
        speedLabel.setText(String.format(Locale.CHINA, "滚动速度：%.1fx", (speedView.getProgress() + 50) / 100f));
    }

    private void applySettings() {
        SeekBar heightView = findViewById(R.id.danmu_height);
        SeekBar opacityView = findViewById(R.id.danmu_opacity);
        SeekBar sizeView = findViewById(R.id.danmu_size);
        SeekBar speedView = findViewById(R.id.danmu_speed);
        DanmuHelper.setMaxLine(heightView.getProgress() + 1);
        DanmuHelper.setAlpha((opacityView.getProgress() + 10) / 100f);
        DanmuHelper.setSizeScale((sizeView.getProgress() + 6) / 10f);
        DanmuHelper.setSpeed((speedView.getProgress() + 50) / 100f);
        if (listener != null) listener.onDanmakuSettingsChanged();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
