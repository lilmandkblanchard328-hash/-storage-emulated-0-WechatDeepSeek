package com.wechat.deepseek;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.app.NotificationManager;
import android.service.notification.NotificationListenerService;

public class MainActivity extends AppCompatActivity {

    private EditText etApiKey;
    private TextView tvStatus;
    private ImageView ivStatusDot;
    private Button btnToggle;
    private Button btnSave;
    private SharedPreferences prefs;
    private boolean isRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("config", MODE_PRIVATE);

        etApiKey = findViewById(R.id.et_api_key);
        tvStatus = findViewById(R.id.tv_status);
        ivStatusDot = findViewById(R.id.iv_status_dot);
        btnToggle = findViewById(R.id.btn_toggle);
        btnSave = findViewById(R.id.btn_save);

        // 恢复已保存的 API Key
        String savedKey = prefs.getString("api_key", "");
        if (!TextUtils.isEmpty(savedKey)) {
            etApiKey.setText(savedKey);
        }

        btnSave.setOnClickListener(v -> {
            String key = etApiKey.getText().toString().trim();
            if (TextUtils.isEmpty(key)) {
                Toast.makeText(this, "API Key 不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString("api_key", key).apply();
            Toast.makeText(this, "API Key 已保存", Toast.LENGTH_SHORT).show();
        });

        btnToggle.setOnClickListener(v -> {
            if (!isRunning) {
                startService();
            } else {
                stopService();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private void startService() {
        String key = prefs.getString("api_key", "");
        if (TextUtils.isEmpty(key)) {
            Toast.makeText(this, "请先输入并保存 API Key", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查无障碍服务
        if (!isAccessibilityEnabled()) {
            Toast.makeText(this, "请开启无障碍服务", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return;
        }

        // 检查通知监听权限
        if (!isNotificationListenerEnabled()) {
            Toast.makeText(this, "请开启通知使用权", Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(
                    "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
            return;
        }

        // 请求忽略电池优化
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                try {
                    startActivity(intent);
                } catch (Exception ignored) {}
            }
        }

        // 启动前台服务
        Intent serviceIntent = new Intent(this, KeepAliveService.class);
        ContextCompat.startForegroundService(this, serviceIntent);

        isRunning = true;
        updateUI();
        Toast.makeText(this, "服务已启动，正在监听微信消息", Toast.LENGTH_SHORT).show();
    }

    private void stopService() {
        Intent serviceIntent = new Intent(this, KeepAliveService.class);
        stopService(serviceIntent);
        isRunning = false;
        updateUI();
        Toast.makeText(this, "服务已停止", Toast.LENGTH_SHORT).show();
    }

    private void updateUI() {
        if (isRunning) {
            tvStatus.setText(R.string.status_running);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.statusRunning));
            ivStatusDot.setImageResource(R.drawable.status_dot_running);
            ivStatusDot.clearAnimation();
            Animation pulse = AnimationUtils.loadAnimation(this, R.anim.pulse);
            ivStatusDot.startAnimation(pulse);
            btnToggle.setText(R.string.stop);
            btnToggle.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            btnToggle.setBackgroundResource(R.drawable.btn_primary_bg);
        } else {
            tvStatus.setText(R.string.status_stopped);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.statusStopped));
            ivStatusDot.setImageResource(R.drawable.status_dot_stopped);
            ivStatusDot.clearAnimation();
            btnToggle.setText(R.string.start);
            btnToggle.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            btnToggle.setBackgroundResource(R.drawable.btn_primary_bg);
        }
    }

    private boolean isAccessibilityEnabled() {
        int enabled = 0;
        String serviceId = getPackageName() + "/" +
            WechatAccessibilityService.class.getCanonicalName();
        try {
            enabled = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Exception e) {
            return false;
        }
        if (enabled == 1) {
            String enabledServices = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabledServices != null && enabledServices.contains(serviceId);
        }
        return false;
    }

    private boolean isNotificationListenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(),
            "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }
}