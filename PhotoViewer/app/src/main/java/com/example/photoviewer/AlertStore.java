package com.example.photoviewer;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * [편의기능] 침입 기록 (데이터 지속성): 받은 침입 알림과 서버별로 마지막에 확인한 게시물 번호를 기기에 저장한다.
 * SQLite DB + 앱 전용 폴더의 사진 파일이라, 앱을 껐다 켜도 남고 서버에 연결할 수 없어도 기록을 볼 수 있다.
 * 마지막 확인 번호가 남아 있으므로 감시를 다시 켜면 꺼 둔 사이의 검출도 알린다.
 */
final class AlertStore extends SQLiteOpenHelper {
    /** 오래된 기록부터 지워 저장 공간을 제한 */
    static final int MAX_RECORDS = 100;
    private static final String DB_NAME = "intrusion_alerts.db";
    private static final String IMAGE_DIR = "alerts";
    private static AlertStore instance;

    private final File imageDir;

    /** 서비스와 화면이 같은 DB 연결을 함께 씀 */
    static synchronized AlertStore get(Context context) {
        if (instance == null) {
            instance = new AlertStore(context, DB_NAME, IMAGE_DIR);
        }
        return instance;
    }

    AlertStore(Context context, String dbName, String imageDirName) {
        super(context.getApplicationContext(), dbName, null, 1);
        imageDir = new File(context.getApplicationContext().getFilesDir(), imageDirName);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE alert (server TEXT NOT NULL, post_id INTEGER NOT NULL, title TEXT NOT NULL, "
                + "objects TEXT NOT NULL, published TEXT NOT NULL, image_path TEXT, missed INTEGER NOT NULL, "
                + "received_at INTEGER NOT NULL, PRIMARY KEY (server, post_id))");
        db.execSQL("CREATE TABLE monitor_state (server TEXT PRIMARY KEY, after_id INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 첫 버전이라 바꿀 것이 없음
    }

    /** 그 서버에서 마지막으로 확인한 게시물 번호. 처음 감시하는 서버면 NO_BASELINE */
    synchronized int loadAfterId(String server) {
        try (Cursor c = getReadableDatabase().query("monitor_state", new String[]{"after_id"},
                "server = ?", new String[]{server}, null, null, null)) {
            return c.moveToFirst() ? c.getInt(0) : IntrusionAlerts.NO_BASELINE;
        }
    }

    synchronized void saveAfterId(String server, int afterId) {
        ContentValues v = new ContentValues();
        v.put("server", server);
        v.put("after_id", afterId);
        getWritableDatabase().insertWithOnConflict("monitor_state", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** 같은 게시물은 한 번만 저장. 사진은 JPEG 파일로 두고 DB 에는 경로만 넣음 */
    synchronized boolean save(String server, Post post, Bitmap image, boolean missed) {
        if (find(server, post.id) != null) {
            return false;
        }
        String imagePath = image == null ? null : writeImage(server, post.id, image);
        AlertRecord record = AlertRecord.of(server, post, imagePath, missed);
        ContentValues v = new ContentValues();
        v.put("server", record.server);
        v.put("post_id", record.postId);
        v.put("title", record.title);
        v.put("objects", record.objects);
        v.put("published", record.published);
        v.put("image_path", record.imagePath);
        v.put("missed", record.missed ? 1 : 0);
        v.put("received_at", System.currentTimeMillis());
        getWritableDatabase().insert("alert", null, v);
        trim();
        return true;
    }

    private String writeImage(String server, int postId, Bitmap image) {
        File file = new File(imageDir, server + "_" + postId + ".jpg");
        if (!imageDir.isDirectory() && !imageDir.mkdirs()) {
            return null;
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            image.compress(Bitmap.CompressFormat.JPEG, 90, out);
            return file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();  // 사진을 못 남겨도 기록은 남김
            return null;
        }
    }

    /** 최근에 받은 알림부터 */
    synchronized List<AlertRecord> list() {
        return query(null, null, null);
    }

    synchronized AlertRecord find(String server, int postId) {
        List<AlertRecord> found = query("server = ? AND post_id = ?", new String[]{server, String.valueOf(postId)}, "1");
        return found.isEmpty() ? null : found.get(0);
    }

    /** 기록과 사진만 지우고, 마지막 확인 번호는 남겨 지난 검출을 다시 알리지 않음 */
    synchronized void clearRecords() {
        for (AlertRecord record : list()) {
            deleteImage(record);
        }
        getWritableDatabase().delete("alert", null, null);
    }

    private void trim() {
        List<AlertRecord> all = list();
        for (int i = MAX_RECORDS; i < all.size(); i++) {
            AlertRecord old = all.get(i);
            deleteImage(old);
            getWritableDatabase().delete("alert", "server = ? AND post_id = ?",
                    new String[]{old.server, String.valueOf(old.postId)});
        }
    }

    private static void deleteImage(AlertRecord record) {
        if (record.imagePath != null) {
            //noinspection ResultOfMethodCallIgnored
            new File(record.imagePath).delete();
        }
    }

    private List<AlertRecord> query(String where, String[] args, String limit) {
        List<AlertRecord> records = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("alert",
                new String[]{"server", "post_id", "title", "objects", "published", "image_path", "missed"},
                where, args, null, null, "received_at DESC, post_id DESC", limit)) {
            while (c.moveToNext()) {
                records.add(new AlertRecord(c.getString(0), c.getInt(1), c.getString(2), c.getString(3),
                        c.getString(4), c.isNull(5) ? null : c.getString(5), c.getInt(6) == 1));
            }
        }
        return records;
    }
}
