package com.example.photoviewer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class YoloPostprocessTest {
    private static final int PERSON = 0;
    private static final int BUS = 5;

    /** 모델 출력 한 줄: 중심 x·y, 너비, 높이, objectness, 클래스 점수 80개 */
    private static float[] row(float cx, float cy, float w, float h, float obj, int cls, float clsScore) {
        float[] r = new float[85];
        r[0] = cx;
        r[1] = cy;
        r[2] = w;
        r[3] = h;
        r[4] = obj;
        r[5 + cls] = clsScore;
        return r;
    }

    @Test
    public void names_are_80_coco_classes() {
        assertEquals(80, YoloPostprocess.NAMES.length);
        assertEquals("person", YoloPostprocess.NAMES[PERSON]);
        assertEquals("bus", YoloPostprocess.NAMES[BUS]);
        assertEquals("toothbrush", YoloPostprocess.NAMES[79]);
    }

    @Test
    public void low_confidence_is_dropped() {
        float[][] rows = {
            row(50, 50, 20, 20, 0.2f, PERSON, 0.99f),   // objectness 부족
            row(50, 50, 20, 20, 0.9f, PERSON, 0.2f),    // obj * cls = 0.18 부족
        };
        assertEquals(0, YoloPostprocess.decode(rows, 1f, 320, 320).size());
    }

    @Test
    public void coordinates_are_restored_to_original_image_and_clamped() {
        float[][] rows = {row(160, 100, 40, 20, 0.9f, BUS, 0.9f), row(5, 5, 40, 40, 0.9f, PERSON, 0.9f)};
        List<YoloPostprocess.Detection> out = YoloPostprocess.decode(rows, 0.5f, 640, 480);
        assertEquals(2, out.size());
        YoloPostprocess.Detection bus = out.get(0).classId == BUS ? out.get(0) : out.get(1);
        assertEquals(280f, bus.x1, 0.01f);
        assertEquals(180f, bus.y1, 0.01f);
        assertEquals(360f, bus.x2, 0.01f);
        assertEquals(220f, bus.y2, 0.01f);
        YoloPostprocess.Detection person = out.get(0).classId == PERSON ? out.get(0) : out.get(1);
        assertEquals(0f, person.x1, 0.01f);  // 이미지 밖은 잘림
        assertEquals(0f, person.y1, 0.01f);
    }

    @Test
    public void nms_keeps_best_of_overlapping_same_class() {
        float[][] rows = {
            row(100, 100, 50, 50, 0.6f, PERSON, 0.9f),
            row(102, 102, 50, 50, 0.9f, PERSON, 0.9f),  // 더 높은 점수, 많이 겹침
            row(250, 250, 50, 50, 0.7f, PERSON, 0.9f),  // 안 겹침
        };
        List<YoloPostprocess.Detection> out = YoloPostprocess.decode(rows, 1f, 320, 320);
        assertEquals(2, out.size());
        assertEquals(0.81f, out.get(0).score, 0.001f);
        assertEquals(0.63f, out.get(1).score, 0.001f);
    }

    @Test
    public void nms_keeps_overlapping_different_classes() {
        float[][] rows = {row(100, 100, 50, 50, 0.9f, PERSON, 0.9f), row(100, 100, 50, 50, 0.8f, BUS, 0.9f)};
        assertEquals(2, YoloPostprocess.decode(rows, 1f, 320, 320).size());
    }

    @Test
    public void iou_of_disjoint_boxes_is_zero() {
        List<YoloPostprocess.Detection> d = YoloPostprocess.decode(
                new float[][]{row(50, 50, 20, 20, 0.9f, PERSON, 0.9f), row(200, 200, 20, 20, 0.9f, PERSON, 0.9f)},
                1f, 320, 320);
        assertEquals(0f, YoloPostprocess.iou(d.get(0), d.get(1)), 0f);
    }

    @Test
    public void summary_and_post_text_formats() {
        float[][] rows = {
            row(50, 50, 20, 20, 0.95f, PERSON, 0.95f),
            row(150, 50, 20, 20, 0.9f, PERSON, 0.9f),
            row(250, 250, 60, 60, 0.8f, BUS, 0.8f),
        };
        List<YoloPostprocess.Detection> d = YoloPostprocess.decode(rows, 1f, 320, 320);
        assertEquals("person 2, bus 1", YoloPostprocess.countSummary(d));
        assertEquals("person, bus, ", YoloPostprocess.postText(d));
    }

    @Test
    public void nothing_detected_gives_empty_text() {
        List<YoloPostprocess.Detection> none = new ArrayList<>(Collections.emptyList());
        assertEquals("", YoloPostprocess.countSummary(none));
        assertEquals("", YoloPostprocess.postText(none));
    }
}
