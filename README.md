# 모바일/웹서비스 프로젝트 공통평가 02 — 침입자 감시 시스템

강의자료 ch08 「Django와 YOLO를 이용한 침입자 감시 시스템」 구현 및 테스트 결과.
YOLOv5(Edge)가 카메라에서 새로 나타난 객체를 검출하면, 그 장면을 Django 블로그 서버에 REST API 로 올리고
웹 브라우저와 Android 앱(PhotoViewer)에서 확인한다.

```
ROOT
├─ PhotoBlogServer/   Django Server  (이미지 블로그 + REST API)
├─ PhotoViewer/       Android Client (게시물 확인)
├─ YOLOv5/            Edge Client    (객체 검출 → 서버 전송)
└─ 모바일웹서비스 프로젝트_공통평가 02_수행 결과 보고서.docx
```

```
[웹캠] → YOLOv5 detect.py ─ 새 객체 출현 시 ─ POST /api_root/Post/ (JWT 토큰) → [Django 블로그 서버] ← 웹 브라우저 / Android 앱
```

| 폴더 | 내용 |
|---|---|
| [YOLOv5](YOLOv5) | [ultralytics/yolov5](https://github.com/ultralytics/yolov5) + 강의자료 8~11p 수정: [`changedetection.py`](YOLOv5/changedetection.py)(객체 출현 변화 감지·전송), [`detect.py`](YOLOv5/detect.py)(`# added` 5줄), [`.vscode/launch.json`](YOLOv5/.vscode/launch.json) |
| [PhotoBlogServer](PhotoBlogServer) | 이미지 블로그 웹 + REST API(`/api_root/Post/`, `/api-token-auth/`), `Authorization: JWT <토큰>` 인증 |
| [PhotoViewer](PhotoViewer) | 서버에 올라온 검출 이미지 목록·상세 확인 (로컬/클라우드 서버 선택) |

## 편의 기능

| 구분 | 기능 | 구현 |
|---|---|---|
| Client | ① 온디바이스 객체 인식 | 앱 안의 YOLOv5n(ONNX)이 사진을 기기에서 직접 인식. 서버 전송 없이, 비행기 모드에서도 동작 |
| Server | ② 로그인·업로드 보안 | `/api-token-auth/` 시도 제한(분당 5회, 초과 429), 사진이 아니거나 5MB 초과 파일 거부(400) |
| Client | ② 로그인·토큰 보호 | 앱에 토큰을 넣지 않고 로그인으로 발급, Android Keystore 로 암호화 저장, 로그아웃 |

## 실행

```bash
# 1) 서버
cd PhotoBlogServer
python -m venv venv && venv/Scripts/activate      # Windows (macOS/Linux: source venv/bin/activate)
pip install -r requirements.txt
python manage.py migrate
python manage.py create_app_user yolo             # YOLOv5 전용 사용자 토큰 출력
python manage.py runserver

# 2) Edge (YOLOv5)
cd YOLOv5
python -m venv venv && venv/Scripts/activate
pip install -r requirements.txt
cp .env.example .env                              # BLOG_HOST, BLOG_TOKEN 입력 (git 에 올리지 않음)
python detect.py --source 0                       # 웹캠

# 3) 앱에서 로그인할 비밀번호 (서버 폴더)
python manage.py changepassword photoviewer
```

YOLOv5 원본: ultralytics/yolov5 `af08ba1` (2026-10-21), AGPL-3.0 ([LICENSE](YOLOv5/LICENSE)).
