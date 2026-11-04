package com.example.photoviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.List;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/**
 * [편의기능] 온디바이스 객체 인식: 앱에 들어 있는 YOLOv5n 모델(assets/yolov5n.onnx)을 휴대폰에서 직접 실행한다.
 * 사진을 서버로 보내지 않고, 인터넷이 없어도 동작한다.
 * 모델은 YOLOv5 폴더에서 python export.py --weights yolov5n.pt --include onnx --imgsz 320 으로 만들었다.
 */
public final class YoloDetector {
    private static final int INPUT_SIZE = 320;
    private static final float PAD = 114f / 255f;  // YOLOv5 letterbox 와 같은 회색
    private static YoloDetector instance;

    private final OrtEnvironment env;
    private final OrtSession session;

    public static final class Result {
        public final List<YoloPostprocess.Detection> detections;
        public final long inferenceMs;

        Result(List<YoloPostprocess.Detection> detections, long inferenceMs) {
            this.detections = detections;
            this.inferenceMs = inferenceMs;
        }
    }

    /** 모델을 한 번만 읽어 재사용 (첫 호출은 백그라운드에서) */
    public static synchronized YoloDetector get(Context context) throws IOException, OrtException {
        if (instance == null) {
            instance = new YoloDetector(context.getApplicationContext());
        }
        return instance;
    }

    private YoloDetector(Context context) throws IOException, OrtException {
        env = OrtEnvironment.getEnvironment();
        try (InputStream in = context.getAssets().open("yolov5n.onnx")) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[64 * 1024];
            int n;
            while ((n = in.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
            session = env.createSession(buf.toByteArray(), new OrtSession.SessionOptions());
        }
    }

    public synchronized Result detect(Bitmap image) throws OrtException {
        // 긴 변을 320 으로 줄이고 남는 오른쪽·아래를 회색으로 채움 (좌표는 scale 만 되돌리면 원본과 맞음)
        float scale = (float) INPUT_SIZE / Math.max(image.getWidth(), image.getHeight());
        int w = Math.max(1, Math.round(image.getWidth() * scale));
        int h = Math.max(1, Math.round(image.getHeight() * scale));
        Bitmap resized = Bitmap.createScaledBitmap(image, w, h, true);
        int[] pixels = new int[w * h];
        resized.getPixels(pixels, 0, w, 0, 0, w, h);

        float[] input = new float[3 * INPUT_SIZE * INPUT_SIZE];
        java.util.Arrays.fill(input, PAD);
        int plane = INPUT_SIZE * INPUT_SIZE;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = pixels[y * w + x];
                int i = y * INPUT_SIZE + x;
                input[i] = ((p >> 16) & 0xFF) / 255f;              // R
                input[plane + i] = ((p >> 8) & 0xFF) / 255f;       // G
                input[2 * plane + i] = (p & 0xFF) / 255f;          // B
            }
        }

        long[] shape = {1, 3, INPUT_SIZE, INPUT_SIZE};
        long start = SystemClock.elapsedRealtime();
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape);
             OrtSession.Result output = session.run(
                     Collections.singletonMap(session.getInputNames().iterator().next(), tensor))) {
            float[][] rows = ((float[][][]) output.get(0).getValue())[0];
            long elapsed = SystemClock.elapsedRealtime() - start;
            return new Result(YoloPostprocess.decode(rows, scale, image.getWidth(), image.getHeight()), elapsed);
        }
    }
}
