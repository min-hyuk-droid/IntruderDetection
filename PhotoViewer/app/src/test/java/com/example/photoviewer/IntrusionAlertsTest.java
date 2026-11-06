package com.example.photoviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 침입 알림: 서버 주소, 응답 해석, 다음 기준 번호, 알림 문구 단위 테스트 */
public class IntrusionAlertsTest {
    private static final String SERVER = "https://example.com";

    private static Post post(int id, String title, String text) throws JSONException {
        return new Post(new JSONObject()
                .put("id", id)
                .put("title", title)
                .put("text", text)
                .put("published_date", "2026-11-06T14:02:42.123456+09:00"));
    }

    @Test
    public void test_url_without_baseline_asks_latest_only() {
        assertEquals(SERVER + "/api_root/Post/alerts/",
                IntrusionAlerts.url(SERVER, IntrusionAlerts.NO_BASELINE));
    }

    @Test
    public void test_url_with_baseline_asks_newer_posts() {
        assertEquals(SERVER + "/api_root/Post/alerts/?after=0", IntrusionAlerts.url(SERVER, 0));
        assertEquals(SERVER + "/api_root/Post/alerts/?after=84", IntrusionAlerts.url(SERVER, 84));
    }

    @Test
    public void test_parse_keeps_server_order() throws JSONException {
        List<Post> posts = IntrusionAlerts.parse(
                "[{\"id\": 5, \"title\": \"bottle\"}, {\"id\": 8, \"title\": \"person\"}]");
        assertEquals(2, posts.size());
        assertEquals(5, posts.get(0).id);
        assertEquals("person", posts.get(1).title);
    }

    @Test
    public void test_parse_empty_array() throws JSONException {
        assertTrue(IntrusionAlerts.parse("[]").isEmpty());
    }

    @Test(expected = JSONException.class)
    public void test_parse_rejects_non_array() throws JSONException {
        IntrusionAlerts.parse("{\"after\": \"잘못된 값\"}");
    }

    @Test
    public void test_next_after_id_uses_largest_id() throws JSONException {
        assertEquals(8, IntrusionAlerts.nextAfterId(Arrays.asList(post(5, "a", ""), post(8, "b", "")), 3));
    }

    @Test
    public void test_next_after_id_keeps_current_when_nothing_new() {
        assertEquals(84, IntrusionAlerts.nextAfterId(Collections.emptyList(), 84));
    }

    @Test
    public void test_next_after_id_on_server_without_detections_starts_from_zero() {
        // 검출이 하나도 없는 서버: 다음 요청부터 첫 검출을 받을 수 있어야 함
        assertEquals(0, IntrusionAlerts.nextAfterId(Collections.emptyList(), IntrusionAlerts.NO_BASELINE));
    }

    @Test
    public void test_title_and_message() throws JSONException {
        Post post = post(84, "bottle", "person, bottle, ");
        assertEquals("침입 감지: bottle", IntrusionAlerts.title(post));
        assertEquals("person, bottle | 2026-11-06 14:02", IntrusionAlerts.message(post));
    }
}
