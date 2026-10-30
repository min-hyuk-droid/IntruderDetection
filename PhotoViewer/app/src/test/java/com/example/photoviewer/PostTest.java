package com.example.photoviewer;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

/** 서버 JSON → 화면 표시 문자열 변환 단위 테스트 (강의자료 ch04 unittest 방식을 JUnit 으로) */
public class PostTest {

    private JSONObject json;

    @Before
    public void setUp() throws Exception {
        json = new JSONObject("{\"id\": 7, \"author\": 1, \"author_name\": \"admin\", "
                + "\"title\": \"은하수 소용돌이\", \"text\": \"본문\", "
                + "\"published_date\": \"2026-10-16T20:15:42.123456+09:00\", "
                + "\"image\": \"https://smh0927.pythonanywhere.com/media/images/a.jpg\"}");
    }

    @Test
    public void test_parse_fields() {
        Post post = new Post(json);
        assertEquals(7, post.id);
        assertEquals("admin", post.authorName);
        assertEquals("은하수 소용돌이", post.title);
        assertEquals("https://smh0927.pythonanywhere.com/media/images/a.jpg", post.imageUrl);
    }

    @Test
    public void test_display_date_cuts_seconds_and_timezone() {
        assertEquals("2026-10-16 20:15", new Post(json).displayDate());
    }

    @Test
    public void test_short_date_for_grid_card() {
        assertEquals("10-16 20:15", new Post(json).shortDate());
    }

    @Test
    public void test_meta_uses_bar_not_middle_dot() {
        // Jua 글꼴에 없는 가운뎃점(·) 대신 | 를 써야 □ 로 보이지 않음
        assertEquals("admin  |  2026-10-16 20:15", new Post(json).meta());
    }

    @Test
    public void test_unpublished_post_has_empty_date() throws Exception {
        json.put("published_date", JSONObject.NULL);
        Post post = new Post(json);
        assertEquals("", post.displayDate());
        assertEquals("", post.shortDate());
    }

    @Test
    public void test_missing_author_name_falls_back_to_author_id() {
        json.remove("author_name");
        assertEquals("1", new Post(json).authorName);
    }
}
