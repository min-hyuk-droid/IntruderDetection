package com.example.photoviewer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** [편의기능] YOLOv5 출력 해석: 신뢰도 필터 + 클래스별 NMS (YOLOv5 utils/general.py 의 non_max_suppression 과 같은 방식) */
public final class YoloPostprocess {
    public static final float CONF_THRES = 0.25f;  // detect.py 기본값
    public static final float IOU_THRES = 0.45f;   // detect.py 기본값

    // YOLOv5 data/coco.yaml 의 80개 클래스 (강의자료 ch08 5p)
    public static final String[] NAMES = {
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
        "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
        "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
        "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
        "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
        "hair drier", "toothbrush"
    };

    private YoloPostprocess() {
    }

    public static final class Detection {
        public final int classId;
        public final float score;
        public final float x1;
        public final float y1;
        public final float x2;
        public final float y2;

        Detection(int classId, float score, float x1, float y1, float x2, float y2) {
            this.classId = classId;
            this.score = score;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        public String name() {
            return NAMES[classId];
        }
    }

    /**
     * @param rows   모델 출력 N x 85: 중심 x, 중심 y, 너비, 높이(입력 이미지 픽셀), objectness, 클래스 80개 점수
     * @param scale  원본 → 입력 축소 비율 (원본 좌표 = 출력 좌표 / scale)
     * @return 점수 높은 순, 원본 이미지 좌표
     */
    public static List<Detection> decode(float[][] rows, float scale, int imageWidth, int imageHeight) {
        List<Detection> candidates = new ArrayList<>();
        for (float[] r : rows) {
            if (r[4] < CONF_THRES) {
                continue;
            }
            int best = 0;
            for (int c = 1; c < NAMES.length; c++) {
                if (r[5 + c] > r[5 + best]) {
                    best = c;
                }
            }
            float score = r[4] * r[5 + best];
            if (score < CONF_THRES) {
                continue;
            }
            candidates.add(new Detection(best, score,
                    clamp((r[0] - r[2] / 2) / scale, imageWidth),
                    clamp((r[1] - r[3] / 2) / scale, imageHeight),
                    clamp((r[0] + r[2] / 2) / scale, imageWidth),
                    clamp((r[1] + r[3] / 2) / scale, imageHeight)));
        }
        return nms(candidates);
    }

    private static float clamp(float v, int max) {
        return Math.max(0, Math.min(v, max));
    }

    static List<Detection> nms(List<Detection> candidates) {
        List<Detection> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble((Detection d) -> d.score).reversed());
        List<Detection> kept = new ArrayList<>();
        for (Detection d : sorted) {
            boolean overlapped = false;
            for (Detection k : kept) {
                if (k.classId == d.classId && iou(k, d) > IOU_THRES) {
                    overlapped = true;
                    break;
                }
            }
            if (!overlapped) {
                kept.add(d);
            }
        }
        return kept;
    }

    static float iou(Detection a, Detection b) {
        float w = Math.min(a.x2, b.x2) - Math.max(a.x1, b.x1);
        float h = Math.min(a.y2, b.y2) - Math.max(a.y1, b.y1);
        if (w <= 0 || h <= 0) {
            return 0;
        }
        float inter = w * h;
        float union = (a.x2 - a.x1) * (a.y2 - a.y1) + (b.x2 - b.x1) * (b.y2 - b.y1) - inter;
        return inter / union;
    }

    /** 화면 표시용: "person 4, bus 1" (점수 높은 물체부터) */
    public static String countSummary(List<Detection> detections) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Detection d : detections) {
            counts.merge(d.name(), 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            sb.append(sb.length() == 0 ? "" : ", ").append(e.getKey()).append(' ').append(e.getValue());
        }
        return sb.toString();
    }

    /** 게시물 내용 자동 입력: YOLOv5 Edge(changedetection.py)의 text 와 같은 형식 "person, bus, " */
    public static String postText(List<Detection> detections) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Detection d : detections) {
            counts.merge(d.name(), 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        for (String name : counts.keySet()) {
            sb.append(name).append(", ");
        }
        return sb.toString();
    }
}
