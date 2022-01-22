package com.idormy.sms.forwarder.sender;

import android.os.Handler;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.idormy.sms.forwarder.utils.CertUtils;
import com.idormy.sms.forwarder.utils.LogUtil;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.reactivex.rxjava3.core.ObservableEmitter;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@SuppressWarnings({"deprecation"})
public class SenderWebNotifyMsg extends SenderBaseMsg {

    static final String TAG = "SenderWebNotifyMsg";

    public static void sendMsg(final long logId, final Handler handError, final ObservableEmitter<Object> emitter, String webServer, String webParams, String secret, String method, String from, String content) throws Exception {
        Log.i(TAG, "sendMsg webServer:" + webServer + " webParams:" + webParams + " from:" + from + " content:" + content);

        if (webServer == null || webServer.isEmpty()) {
            return;
        }

        Long timestamp = System.currentTimeMillis();
        String sign = "";
        if (secret != null && !secret.isEmpty()) {
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            sign = URLEncoder.encode(new String(Base64.encode(signData, Base64.NO_WRAP)), "UTF-8");
            Log.i(TAG, "sign:" + sign);
        }

        Request request;
        if (method.equals("GET")) {
            webServer += (webServer.contains("?") ? "&" : "?") + "from=" + URLEncoder.encode(from, "UTF-8");
            webServer += "&content=" + URLEncoder.encode(content, "UTF-8");
            if (secret != null && !secret.isEmpty()) {
                webServer += "&timestamp=" + timestamp;
                webServer += "&sign=" + sign;
            }

            Log.d(TAG, "method = GET, Url = " + webServer);
            request = new Request.Builder().url(webServer).get().build();
        } else if (webParams != null && webParams.contains("[msg]")) {
            String bodyMsg;
            String Content_Type = "application/x-www-form-urlencoded";
            if (webParams.startsWith("{")) {
                bodyMsg = content.replace("\n", "\\n");
                bodyMsg = webParams.replace("[msg]", bodyMsg);
                Content_Type = "application/json;charset=utf-8";
            } else {
                bodyMsg = webParams.replace("[msg]", URLEncoder.encode(content, "UTF-8"));
            }
            RequestBody body = RequestBody.create(MediaType.parse(Content_Type), bodyMsg);
            request = new Request.Builder()
                    .url(webServer)
                    .addHeader("Content-Type", Content_Type)
                    .method("POST", body)
                    .build();
            Log.d(TAG, "method = POST webParams, Body = " + bodyMsg);
        } else {
            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("from", from)
                    .addFormDataPart("content", content);
            if (secret != null && !secret.isEmpty()) {
                builder.addFormDataPart("timestamp", String.valueOf(timestamp));
                builder.addFormDataPart("sign", sign);
            }

            RequestBody body = builder.build();
            Log.d(TAG, "method = POST, Body = " + body);
            request = new Request.Builder().url(webServer).method("POST", body).build();
        }

        OkHttpClient client = new OkHttpClient().newBuilder()
                //忽略https证书
                .sslSocketFactory(CertUtils.getSSLSocketFactory(), CertUtils.getX509TrustManager())
                .hostnameVerifier(CertUtils.getHostnameVerifier())
                .build();
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull final IOException e) {
                LogUtil.updateLog(logId, 0, e.getMessage());
                Toast(handError, TAG, "发送失败：" + e.getMessage());
                if (emitter != null) emitter.onError(new Exception("RxJava 请求接口异常..."));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String responseStr = Objects.requireNonNull(response.body()).string();
                Log.d(TAG, "Response：" + response.code() + "，" + responseStr);
                Toast(handError, TAG, "发送状态：" + responseStr);

                //返回http状态200即为成功
                if (200 == response.code()) {
                    LogUtil.updateLog(logId, 2, responseStr);
                } else {
                    LogUtil.updateLog(logId, 0, responseStr);
                }
            }
        });

    }

}