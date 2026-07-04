package com.wechat.deepseek;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/**
 * 通知监听服务：检测微信新消息通知，触发处理流程
 * （作为无障碍服务的补充触发源）
 */
public class NotificationListener extends NotificationListenerService {

    private static final String TAG = "WechatDeepSeek";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String pkg = sbn.getPackageName();
        if (!"com.tencent.mm".equals(pkg)) return;

        // 提取通知文本
        CharSequence ticker = sbn.getNotification().tickerText;
        String text = "";
        if (ticker != null) {
            text = ticker.toString();
        }

        if (text.contains("新消息") || text.contains("发来一条消息")) {
            Log.i(TAG, "检测到微信新消息通知: " + text);
            // 无障碍服务会接管后续的读取和回复流程
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {}
}