package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.lxj.xpopup.core.BottomPopupView;
import cat.ereza.customactivityoncrash.CrashLogStore;

import org.jetbrains.annotations.NotNull;

public class AboutDialog extends BottomPopupView {

    public AboutDialog(@NonNull @NotNull Context context) {
        super(context);
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.dialog_about;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        findViewById(R.id.iv_close).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });
        TextView logView = findViewById(R.id.tv_error_log);
        showLog(logView);
        findViewById(R.id.btn_clear_log).setOnClickListener(view -> {
            CrashLogStore.clear(getContext());
            showLog(logView);
        });
    }

    private void showLog(TextView logView) {
        String log = CrashLogStore.read(getContext());
        if (TextUtils.isEmpty(log)) {
            logView.setText("暂无错误日志");
            return;
        }
        if (log.length() > 16000) log = log.substring(log.length() - 16000);
        logView.setText(log);
    }
}