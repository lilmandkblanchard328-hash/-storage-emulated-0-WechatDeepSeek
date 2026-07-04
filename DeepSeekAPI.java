package com.wechat.deepseek;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * DeepSeek API 调用封装
 */
public class DeepSeekAPI {

    private static final String TAG = "DeepSeekAPI";
    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String apiKey;
    private final OkHttpClient client;
    private final Gson gson;

    public interface Callback {
        void onSuccess(String reply);
        void onError(String error);
    }

    public DeepSeekAPI(String apiKey) {
        this.apiKey = apiKey;
        this.gson = new Gson();
        this.client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build();
    }

    public void getReply(String message, Callback callback) {
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("API Key 未设置");
            return;
        }

        // 构建请求体
        JsonObject body = new JsonObject();
        body.addProperty("model", "deepseek-chat");
        body.addProperty("stream", false);

        JsonArray messages = new JsonArray();

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", "你是一个智能助理，请简洁准确地回答问题。");
        messages.add(systemMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", message);
        messages.add(userMsg);

        body.add("messages", messages);

        // 构建请求
        Request request = new Request.Builder()
            .url(API_URL)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(gson.toJson(body), JSON))
            .build();

        // 异步执行
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "网络请求失败", e);
                callback.onError("网络错误: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ?
                    response.body().string() : "";

                if (response.isSuccessful()) {
                    try {
                        JsonObject result = gson.fromJson(responseBody, JsonObject.class);
                        String content = result
                            .getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();

                        callback.onSuccess(content.trim());
                    } catch (Exception e) {
                        Log.e(TAG, "解析响应失败: " + responseBody, e);
                        callback.onError("解析失败");
                    }
                } else {
                    Log.e(TAG, "API返回错误: " + response.code() + " " + responseBody);
                    callback.onError("API错误: " + response.code());
                }
                response.close();
            }
        });
    }
}