package com.example.photoviewer;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * [편의기능] 침입 알림: "감시 시작" 을 누르면 앱을 닫아도 서버의 alerts API 를 10초마다 확인하고,
 * YOLOv5 가 새로 올린 검출이 있으면 사진이 든 알림을 띄운다. 알림을 누르면 그 게시물을 연다.
 */
public class IntrusionMonitorService extends Service {
    static final String EXTRA_SERVER_URL = "server_url";
    static final String EXTRA_SERVER_INDEX = "server_index";
    static final String EXTRA_SERVER_NAME = "server_name";
    static final String EXTRA_POST_ID = "alert_post_id";
    private static final String ACTION_STOP = "com.example.photoviewer.STOP_MONITOR";
    private static final String CHANNEL_STATUS = "monitor_status";
    private static final String CHANNEL_ALERT = "intrusion_alert";
    // 시스템이 알림을 자동으로 묶으면 눌러도 그 게시물이 열리지 않아 상태·침입 알림의 그룹을 나눔
    private static final String GROUP_STATUS = "monitor_status";
    private static final String GROUP_ALERT = "intrusion_alert";
    private static final int STATUS_ID = 1;
    private static final int ENDED_ID = 2;
    private static final int ALERT_ID_BASE = 1000;
    private static final long POLL_MS = 10000;
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;

    /** 화면의 감시 버튼 표시에 사용 */
    static volatile boolean running = false;

    private Thread worker;
    private int serverIndex;
    private String serverName;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        String serverUrl = intent.getStringExtra(EXTRA_SERVER_URL);
        serverIndex = intent.getIntExtra(EXTRA_SERVER_INDEX, 0);
        serverName = intent.getStringExtra(EXTRA_SERVER_NAME);
        createChannels();
        ServiceCompat.startForeground(this, STATUS_ID, statusNotification(serverName + " 서버 감시 중 | 10초마다 확인"),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0);
        running = true;
        // 감시 중에 서버를 바꾸면 이전 서버 확인을 멈추고 새 서버를 처음부터 확인
        stopWorker();
        worker = new Thread(() -> pollLoop(serverUrl));
        worker.start();
        return START_NOT_STICKY;
    }

    private void pollLoop(String serverUrl) {
        int afterId = IntrusionAlerts.NO_BASELINE;
        boolean connected = true;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<Post> posts = IntrusionAlerts.parse(fetch(IntrusionAlerts.url(serverUrl, afterId)));
                if (Thread.currentThread().isInterrupted()) {
                    return;  // 응답을 기다리는 사이 감시를 멈췄거나 서버를 바꿈
                }
                if (afterId != IntrusionAlerts.NO_BASELINE) {  // 처음 받은 검출은 기준점이라 알리지 않음
                    for (Post post : posts) {
                        notifyAlert(post);
                    }
                }
                afterId = IntrusionAlerts.nextAfterId(posts, afterId);
                if (!connected) {
                    updateStatus(serverName + " 서버 감시 중 | 10초마다 확인");
                    connected = true;
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (connected) {  // 서버가 잠시 꺼져도 감시는 멈추지 않고 다시 시도
                    updateStatus(serverName + " 서버에 연결할 수 없습니다 | 다시 시도 중");
                    connected = false;
                }
            }
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private String fetch(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code);
            }
            StringBuilder result = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
            }
            return result.toString();
        } finally {
            conn.disconnect();
        }
    }

    /** 사진을 받지 못해도 알림은 보낸다 (침입 사실을 먼저 알리는 것이 중요) */
    private Bitmap downloadImage(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            try (InputStream in = conn.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            } finally {
                conn.disconnect();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void notifyAlert(Post post) {
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_POST_ID, post.id)
                .putExtra(EXTRA_SERVER_INDEX, serverIndex);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_intrusion)
                .setContentTitle(IntrusionAlerts.title(post))
                .setContentText(IntrusionAlerts.message(post))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setGroup(GROUP_ALERT)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(this, post.id, open,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        Bitmap image = downloadImage(post.imageUrl);
        if (image != null) {
            builder.setLargeIcon(image).setStyle(new NotificationCompat.BigPictureStyle()
                    .bigPicture(image)
                    .setSummaryText(IntrusionAlerts.message(post)));
        }
        notifyIfAllowed(ALERT_ID_BASE + post.id, builder.build());
    }

    private Notification statusNotification(String text) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        Intent stop = new Intent(this, IntrusionMonitorService.class).setAction(ACTION_STOP);
        return new NotificationCompat.Builder(this, CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_intrusion)
                .setContentTitle("침입자 감시")
                .setContentText(text)
                .setGroup(GROUP_STATUS)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE))
                .addAction(0, getString(R.string.monitor_stop),
                        PendingIntent.getService(this, 0, stop, PendingIntent.FLAG_IMMUTABLE))
                .build();
    }

    private void updateStatus(String text) {
        notifyIfAllowed(STATUS_ID, statusNotification(text));
    }

    private void notifyIfAllowed(int id, Notification notification) {
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        if (manager.areNotificationsEnabled()) {
            manager.notify(id, notification);
        }
    }

    private void createChannels() {
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        manager.createNotificationChannel(new NotificationChannelCompat.Builder(
                CHANNEL_STATUS, NotificationManagerCompat.IMPORTANCE_LOW).setName("감시 상태").build());
        manager.createNotificationChannel(new NotificationChannelCompat.Builder(
                CHANNEL_ALERT, NotificationManagerCompat.IMPORTANCE_HIGH).setName("침입 알림").build());
    }

    private void stopWorker() {
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
    }

    /** Android 15 는 dataSync 서비스를 24시간 중 6시간까지만 허용: 제한에 닿으면 사용자에게 알리고 바로 멈춤 */
    @Override
    public void onTimeout(int startId, int fgsType) {
        notifyIfAllowed(ENDED_ID, new NotificationCompat.Builder(this, CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_intrusion)
                .setContentTitle("침입자 감시가 멈췄습니다")
                .setContentText("Android 실행 시간 제한(6시간)에 닿았습니다. 앱에서 다시 시작하세요.")
                .build());
        stopSelf();
    }

    @Override
    public void onDestroy() {
        running = false;
        stopWorker();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
