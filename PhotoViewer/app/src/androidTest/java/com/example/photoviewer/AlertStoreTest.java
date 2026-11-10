package com.example.photoviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

/** 침입 기록 저장소: 에뮬레이터·휴대폰의 실제 SQLite 와 파일로 시험 (./gradlew connectedDebugAndroidTest) */
@RunWith(AndroidJUnit4.class)
public class AlertStoreTest {
    private static final String DB = "alert_store_test.db";
    private static final String IMAGES = "alert_store_test_images";
    private Context context;
    private AlertStore store;

    private static Post post(int id, String title, String text) throws JSONException {
        return new Post(new JSONObject().put("id", id).put("title", title).put("text", text)
                .put("published_date", "2026-11-10T14:02:42.123456+09:00"));
    }

    private static Bitmap image() {
        Bitmap bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.rgb(255, 176, 32));
        return bitmap;
    }

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(DB);
        store = new AlertStore(context, DB, IMAGES);
    }

    @After
    public void tearDown() {
        store.close();
        context.deleteDatabase(DB);
        File dir = new File(context.getFilesDir(), IMAGES);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
    }

    @Test
    public void after_id_is_kept_per_server() {
        assertEquals(IntrusionAlerts.NO_BASELINE, store.loadAfterId("cloud"));
        store.saveAfterId("cloud", 15);
        store.saveAfterId("cloud", 17);
        store.saveAfterId("local", 84);
        assertEquals(17, store.loadAfterId("cloud"));
        assertEquals(84, store.loadAfterId("local"));
    }

    @Test
    public void saved_alert_has_record_and_image_file() throws JSONException {
        assertTrue(store.save("cloud", post(16, "bus", "person, bus,"), image(), true));
        AlertRecord record = store.find("cloud", 16);
        assertNotNull(record);
        assertEquals("놓친 침입: bus", record.heading());
        assertEquals("person, bus", record.objects);
        assertEquals("2026-11-10 14:02", record.published);
        assertNotNull(BitmapFactory.decodeFile(record.imagePath));
    }

    @Test
    public void same_post_is_saved_once() throws JSONException {
        assertTrue(store.save("cloud", post(16, "bus", "bus,"), image(), false));
        assertFalse(store.save("cloud", post(16, "bus", "bus,"), image(), false));
        assertTrue(store.save("local", post(16, "cup", "cup,"), image(), false));  // 서버가 다르면 다른 게시물
        assertEquals(2, store.list().size());
    }

    @Test
    public void alert_without_image_is_still_saved() throws JSONException {
        store.save("cloud", post(16, "bus", "bus,"), null, false);
        assertNull(store.find("cloud", 16).imagePath);
    }

    @Test
    public void list_is_newest_first() throws JSONException {
        store.save("cloud", post(16, "bus", "bus,"), image(), false);
        store.save("cloud", post(17, "cup", "cup,"), image(), false);
        List<AlertRecord> records = store.list();
        assertEquals(17, records.get(0).postId);
        assertEquals(16, records.get(1).postId);
    }

    @Test
    public void data_survives_reopening_the_database() throws JSONException {
        store.save("cloud", post(16, "bus", "bus,"), image(), false);
        store.saveAfterId("cloud", 16);
        store.close();
        store = new AlertStore(context, DB, IMAGES);  // 앱을 다시 켠 것과 같음
        assertEquals(16, store.loadAfterId("cloud"));
        assertEquals("bus", store.find("cloud", 16).title);
    }

    @Test
    public void old_records_are_trimmed_with_their_images() throws JSONException {
        for (int id = 1; id <= AlertStore.MAX_RECORDS + 2; id++) {
            store.save("cloud", post(id, "bus", "bus,"), image(), false);
        }
        assertEquals(AlertStore.MAX_RECORDS, store.list().size());
        assertNull(store.find("cloud", 1));
        assertFalse(new File(new File(context.getFilesDir(), IMAGES), "cloud_1.jpg").exists());
        assertNotNull(store.find("cloud", AlertStore.MAX_RECORDS + 2));
    }

    @Test
    public void clearing_records_keeps_after_id() throws JSONException {
        store.save("cloud", post(16, "bus", "bus,"), image(), false);
        String path = store.find("cloud", 16).imagePath;
        store.saveAfterId("cloud", 16);
        store.clearRecords();
        assertTrue(store.list().isEmpty());
        assertFalse(new File(path).exists());
        assertEquals(16, store.loadAfterId("cloud"));
    }
}
