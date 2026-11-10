package com.example.photoviewer;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** [편의기능] 침입 알림: 서버 alerts API 의 주소·응답·알림 문구를 다룬다 (안드로이드 없이 단위 테스트 가능) */
final class IntrusionAlerts {
    /** 아직 기준이 되는 게시물 번호를 받지 못한 상태 */
    static final int NO_BASELINE = -1;

    private IntrusionAlerts() {
    }

    /** 기준 번호가 없으면 가장 최근 검출 1개만, 있으면 그 번호 이후의 새 검출을 요청한다 */
    static String url(String serverUrl, int afterId) {
        String url = serverUrl + "/api_root/Post/alerts/";
        return afterId == NO_BASELINE ? url : url + "?after=" + afterId;
    }

    static List<Post> parse(String json) throws JSONException {
        JSONArray array = new JSONArray(json);
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            posts.add(new Post(array.getJSONObject(i)));
        }
        return posts;
    }

    /** 다음 요청에 쓸 기준 번호. 새 검출이 없으면 지금 번호를 유지하고, 아직 검출이 없는 서버는 0 부터 본다 */
    static int nextAfterId(List<Post> posts, int afterId) {
        int next = Math.max(afterId, 0);
        for (Post post : posts) {
            next = Math.max(next, post.id);
        }
        return next;
    }

    /** 감시를 꺼 둔 사이 올라온 검출(다시 켰을 때 처음 받은 것)은 "놓친 침입" 으로 구분 */
    static String heading(String title, boolean missed) {
        return (missed ? "놓친 침입: " : "침입 감지: ") + title;
    }

    static String title(Post post, boolean missed) {
        return heading(post.title, missed);
    }

    /** "person, bottle," -> "person, bottle" */
    static String objects(Post post) {
        String objects = post.text.trim();
        if (objects.endsWith(",")) {
            objects = objects.substring(0, objects.length() - 1);
        }
        return objects;
    }

    /** "person, bottle," -> "person, bottle | 2026-11-06 14:02" */
    static String message(Post post) {
        return objects(post) + " | " + post.displayDate();
    }
}
