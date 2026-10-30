package com.example.photoviewer;

import org.json.JSONObject;

/** 서버 /api_root/Post/ 의 게시물 1개 (JSON Object) — [편의기능] 제목·작성자·게시일 표시, 상세보기, 삭제에 사용 */
public class Post {
    public final int id;
    public final int author;
    public final String authorName;
    public final String title;
    public final String text;
    public final String publishedDate;
    public final String imageUrl;

    public Post(JSONObject json) {
        id = json.optInt("id");
        author = json.optInt("author");
        authorName = json.optString("author_name", String.valueOf(author));
        title = json.optString("title");
        text = json.optString("text");
        publishedDate = json.isNull("published_date") ? "" : json.optString("published_date");
        imageUrl = json.optString("image");
    }

    /** "2026-10-13T21:13:00.123+09:00" -> "2026-10-13 21:13" */
    public String displayDate() {
        if (publishedDate.length() < 16) {
            return publishedDate;
        }
        return publishedDate.substring(0, 16).replace('T', ' ');
    }

    /** 2열 격자 카드용 짧은 날짜: "2026-10-16 20:15" -> "10-16 20:15" */
    public String shortDate() {
        String d = displayDate();
        return d.length() >= 16 ? d.substring(5) : d;
    }

    public String meta() {
        return authorName + "  |  " + displayDate();  // Jua 글꼴에 없는 가운뎃점(·) 대신 | 사용
    }
}
