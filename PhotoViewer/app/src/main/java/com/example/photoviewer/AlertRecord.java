package com.example.photoviewer;

/** [편의기능] 침입 기록 1건: 기기 DB 에 저장된 알림 (서버 연결 없이 보여 줄 내용만 가짐) */
final class AlertRecord {
    final String server;
    final int postId;
    final String title;
    final String objects;
    final String published;
    final String imagePath;
    final boolean missed;

    AlertRecord(String server, int postId, String title, String objects, String published,
                String imagePath, boolean missed) {
        this.server = server;
        this.postId = postId;
        this.title = title;
        this.objects = objects;
        this.published = published;
        this.imagePath = imagePath;
        this.missed = missed;
    }

    static AlertRecord of(String server, Post post, String imagePath, boolean missed) {
        return new AlertRecord(server, post.id, post.title, IntrusionAlerts.objects(post), post.displayDate(),
                imagePath, missed);
    }

    /** 감시를 꺼 둔 사이 올라온 검출은 "놓친 침입" 으로 구분 */
    String heading() {
        return IntrusionAlerts.heading(title, missed);
    }

    /** "클라우드 15번 | 2026-11-06 12:20" */
    String meta(String serverName) {
        return serverName + " " + postId + "번 | " + published;
    }
}
