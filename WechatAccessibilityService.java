package com.wechat.deepseek;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.IOException;
import java.util.List;

/**
 * 无障碍服务：读取微信消息 + 模拟输入回复
 */
public class WechatAccessibilityService extends AccessibilityService {

    private static final String TAG = "WechatDeepSeek";
    private static final String WECHAT_PKG = "com.tencent.mm";
    private DeepSeekAPI deepSeekAPI;
    private boolean processing = false;

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences prefs = getSharedPreferences("config", MODE_PRIVATE);
        String apiKey = prefs.getString("api_key", "");
        deepSeekAPI = new DeepSeekAPI(apiKey);
        Log.i(TAG, "无障碍服务已创建");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (processing) return;

        String pkg = event.getPackageName() != null ?
            event.getPackageName().toString() : "";

        // 只在微信内处理
        if (!WECHAT_PKG.equals(pkg)) return;

        // 监听窗口内容变化（新消息到达时微信会更新UI）
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            handleWechatContentChanged();
        }
    }

    private void handleWechatContentChanged() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        try {
            // 1. 找聊天列表中的最新未读消息
            AccessibilityNodeInfo latestChat = findLatestUnreadChat(root);
            if (latestChat == null) return;

            // 2. 点击进入聊天
            performClick(latestChat);
            sleep(2000);

            // 刷新根节点
            root = getRootInActiveWindow();
            if (root == null) return;

            // 3. 获取最后一条对方消息
            String lastMessage = getLastIncomingMessage(root);
            if (lastMessage == null || lastMessage.isEmpty()) return;

            Log.i(TAG, "收到消息: " + lastMessage);

            // 标记处理中，避免重复
            processing = true;

            // 4. 调用 DeepSeek API
            deepSeekAPI.getReply(lastMessage, new DeepSeekAPI.Callback() {
                @Override
                public void onSuccess(String reply) {
                    Log.i(TAG, "DeepSeek回复: " + reply);
                    sendReply(reply);
                    processing = false;
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "API错误: " + error);
                    processing = false;
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "处理异常", e);
            processing = false;
        } finally {
            root.recycle();
        }
    }

    private AccessibilityNodeInfo findLatestUnreadChat(AccessibilityNodeInfo root) {
        // 查找未读标记（小红点/数字角标）
        // 微信聊天列表的未读标记通常在 TextView 上有未读数
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(
            "com.tencent.mm:id/e6f");
        if (nodes != null && !nodes.isEmpty()) {
            return nodes.get(0);
        }

        // 备选：查找带未读数字的 TextView
        nodes = root.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/av2");
        if (nodes != null && !nodes.isEmpty()) {
            AccessibilityNodeInfo node = nodes.get(0);
            if (node.getParent() != null) {
                return node.getParent();
            }
        }
        return null;
    }

    private String getLastIncomingMessage(AccessibilityNodeInfo root) {
        // 查找消息气泡
        // 微信消息气泡 ID 可能因版本不同而变化，这里列出多个候选
        String[] possibleIds = {
            "com.tencent.mm:id/bhn",
            "com.tencent.mm:id/bho",
            "com.tencent.mm:id/avq",
            "com.tencent.mm:id/avr"
        };

        for (String id : possibleIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) {
                // 取最后一个消息（最新的）
                AccessibilityNodeInfo last = nodes.get(nodes.size() - 1);
                if (last.getText() != null) {
                    return last.getText().toString();
                }
            }
        }
        return null;
    }

    private void sendReply(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        try {
            // 找输入框
            AccessibilityNodeInfo inputBox = findInputBox(root);
            if (inputBox == null) {
                Log.w(TAG, "未找到输入框");
                return;
            }

            // 聚焦输入框
            inputBox.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            sleep(200);

            // 设置文本
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            inputBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            sleep(500);

            // 找发送按钮
            AccessibilityNodeInfo sendBtn = findSendButton(root);
            if (sendBtn != null) {
                performClick(sendBtn);
                Log.i(TAG, "回复已发送");
            }
        } finally {
            root.recycle();
        }
    }

    private AccessibilityNodeInfo findInputBox(AccessibilityNodeInfo root) {
        // 按 EditText 类名找
        List<AccessibilityNodeInfo> edits = root.findAccessibilityNodeInfosByViewId(
            "com.tencent.mm:id/byl");
        if (edits != null && !edits.isEmpty()) {
            return edits.get(0);
        }

        // 备选 ID
        String[] ids = {"com.tencent.mm:id/bym", "com.tencent.mm:id/byn"};
        for (String id : ids) {
            edits = root.findAccessibilityNodeInfosByViewId(id);
            if (edits != null && !edits.isEmpty()) return edits.get(0);
        }

        // 按描述找
        edits = root.findAccessibilityNodeInfosByViewId("com.tencent.mm:id/byk");
        if (edits != null && !edits.isEmpty()) return edits.get(0);

        return null;
    }

    private AccessibilityNodeInfo findSendButton(AccessibilityNodeInfo root) {
        // 发送按钮常见 ID
        String[] ids = {"com.tencent.mm:id/e1f", "com.tencent.mm:id/e1g"};
        for (String id : ids) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (nodes != null && !nodes.isEmpty()) return nodes.get(0);
        }

        // 按文字 "发送" 查找
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("发送");
        if (nodes != null && !nodes.isEmpty()) return nodes.get(0);

        return null;
    }

    private void performClick(AccessibilityNodeInfo node) {
        if (node == null) return;
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "无障碍服务被中断");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "无障碍服务已销毁");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }
}