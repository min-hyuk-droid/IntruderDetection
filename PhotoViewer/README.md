# PhotoViewer — 포토 블로그 Android 클라이언트

강의자료 ch05 「포토블로그를 위한 모바일 앱」 — Java, minSdk 24 / targetSdk 35, AGP 8.7.3, Gradle 8.10.2

## 설정

별도 설정 없이 빌드된다. 서버 주소만 `app/build.gradle` 에 있고, 토큰은 앱에 넣지 않는다. 글 올리기·삭제를 할 때 앱에서 로그인하면 토큰을 받는다.

로그인할 사용자의 비밀번호는 서버에서 정한다: `python manage.py changepassword photoviewer`

## 빌드 / 실행

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> Windows 에서 프로젝트 경로에 한글이 있으면 Android Gradle Plugin 이 빌드를 거부하므로
> 영문 경로(예: `C:\dev\build\photoviewer`)로 복사해서 빌드했다.

## 기능

| 기능 | 구현 |
|---|---|
| 동기화 | `onClickDownload` → `CloadImage extends AsyncTask<String, Integer, List<Bitmap>>`: `GET /api_root/Post/` (Token 인증) → JSON Array 파싱 → 이미지 다운로드 → RecyclerView 표시 (강의자료 18~23p 구조 그대로) |
| 새로운 이미지 게시 (Upload, **Post 에 따른 UI 제공**) | `onClickUpload` → Post 모델 필드대로 제목(title, 필수·200자)·내용(text, 필수) 입력 + [앨범에서 선택](Photo Picker) 또는 [사진 촬영]으로 이미지(image) 선택·미리보기 → 입력 검사 → `PutPost extends AsyncTask<String, Void, Void>` (강의자료 19p): `POST /api_root/Post/` multipart (큰 사진 축소·EXIF 회전 반영) → 업로드 중 버튼 잠금 → 201 이면 목록 재동기화, 400 이면 필드 오류 표시 |
| *(편의)* 게시물 카드 / 상세보기 | 한 줄에 2장(가로 화면 4장) 정사각형 카드에 제목·게시일 표시, 탭하면 큰 이미지·작성자·본문을 보여주는 상세 다이얼로그 |
| *(편의)* 실시간 검색 | 검색창에 입력하면 엔터 없이 0.4초 뒤 `GET /api_root/Post/?search=키워드` (한글은 Gboard 한국어 자판, PC 키보드는 Ctrl+Space 로 한/영 전환) |
| *(편의)* 카메라로 찍어서 바로 게시 | 게시 화면 [사진 촬영] → 카메라 앱(`ActivityResultContracts.TakePicture`) → `FileProvider` 로 앱 캐시에 저장된 사진을 미리보기·게시 |
| *(편의)* 길게 눌러 삭제 | 확인 다이얼로그 → `DELETE /api_root/Post/{id}/` (서버는 작성자만 허용, 403 안내) |
| *(편의)* 자동 동기화 / 당겨서 새로고침 | 앱을 켜면 바로 동기화, 목록을 아래로 당기면 다시 받아옴 (`SwipeRefreshLayout`) |
| 서버 선택 | 로컬(`http://10.0.2.2:8000`) ↔ 클라우드(PythonAnywhere) 스피너, 선택 값 저장, 바꾸면 바로 동기화 |
| *(공통평가02 편의 ①)* 온디바이스 객체 인식 | 게시 화면에서 사진을 고르면 앱에 든 YOLOv5n(`assets/yolov5n.onnx`, onnxruntime-android)으로 기기 안에서 인식: 박스 표시, 인식 시간·물체 수 표시, 빈 제목·내용 자동 입력. 사진을 서버로 보내지 않고 인터넷 없이 동작 |
| *(공통평가02 편의 ②)* 침입 알림 | [감시 시작] → 포그라운드 서비스(`IntrusionMonitorService`)가 앱을 닫아도 `GET /api_root/Post/alerts/?after=번호` 를 10초마다 확인 → 새 검출이 있으면 사진이 든 알림 → 누르면 그 게시물 상세보기. 서버가 꺼져도 다시 시도하고 상태를 표시. Android 15 의 실행 시간 제한(24시간 중 6시간)에 닿으면 알리고 멈춤 |
| 로그인·토큰 보호 | `POST /api-token-auth/` 로그인 → 토큰을 Android Keystore 키(AES-GCM)로 암호화해 저장(`SecureTokenStore`), 비밀번호는 저장하지 않음. 조회는 로그인 없이 가능, 글 올리기·삭제는 로그인 필요, 로그아웃하면 토큰 삭제, 서버 응답 400·429 안내 |

모델 다시 만들기 (YOLOv5 폴더): `pip install onnx onnxscript` 후 `python export.py --weights yolov5n.pt --include onnx --imgsz 320`, 나온 파일을 `app/src/main/assets/` 로 복사.

화면: 크림 배경 + 아이콘의 노랑·검정, 글꼴 Jua ([OFL](licenses/Jua-OFL.txt)), 동기화·게시 버튼은 화면 아래.

단위 테스트: `./gradlew testDebugUnitTest` ([`PostTest.java`](app/src/test/java/com/example/photoviewer/PostTest.java) 6개, [`IntrusionAlertsTest.java`](app/src/test/java/com/example/photoviewer/IntrusionAlertsTest.java) 9개, [`YoloPostprocessTest.java`](app/src/test/java/com/example/photoviewer/YoloPostprocessTest.java) 8개)

## 주요 파일

- [`IntrusionMonitorService.java`](app/src/main/java/com/example/photoviewer/IntrusionMonitorService.java), [`IntrusionAlerts.java`](app/src/main/java/com/example/photoviewer/IntrusionAlerts.java) — 침입 알림 서비스, 주소·응답·문구 처리
- [`MainActivity.java`](app/src/main/java/com/example/photoviewer/MainActivity.java) — 동기화/자동 동기화·당겨서 새로고침/업로드/검색/삭제/서버 선택
- [`ImageAdapter.java`](app/src/main/java/com/example/photoviewer/ImageAdapter.java) — RecyclerView 어댑터
- [`Post.java`](app/src/main/java/com/example/photoviewer/Post.java) — 게시물 JSON 모델
- [`YoloDetector.java`](app/src/main/java/com/example/photoviewer/YoloDetector.java), [`YoloPostprocess.java`](app/src/main/java/com/example/photoviewer/YoloPostprocess.java) — 온디바이스 추론, 신뢰도 필터·NMS
- [`SecureTokenStore.java`](app/src/main/java/com/example/photoviewer/SecureTokenStore.java) — 토큰 암호화 저장
- [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml), [`network_security_config.xml`](app/src/main/res/xml/network_security_config.xml) — 강의자료 12p 의 권한 4개(INTERNET, WRITE/READ/MANAGE_EXTERNAL_STORAGE), 평문(HTTP) 통신 허용(13p), 카메라용 FileProvider
- [`activity_main.xml`](app/src/main/res/layout/activity_main.xml), [`item_image.xml`](app/src/main/res/layout/item_image.xml), [`dialog_upload.xml`](app/src/main/res/layout/dialog_upload.xml)
