# PhotoViewer — 포토 블로그 Android 클라이언트

강의자료 ch05 「포토블로그를 위한 모바일 앱」 — Java, minSdk 24 / targetSdk 35, AGP 8.7.3, Gradle 8.10.2

## 설정

서버 주소와 토큰은 git 에 올리지 않는 `secrets.properties` 에서 읽어 `BuildConfig` 로 들어간다.

```bash
cp secrets.properties.example secrets.properties
# LOCAL_TOKEN / REMOTE_URL / REMOTE_TOKEN 입력
# 토큰 발급(서버): python manage.py drf_create_token <username>
```

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

화면: 크림 배경 + 아이콘의 노랑·검정, 글꼴 Jua ([OFL](licenses/Jua-OFL.txt)), 동기화·게시 버튼은 화면 아래.

단위 테스트: `./gradlew testDebugUnitTest` ([`PostTest.java`](app/src/test/java/com/example/photoviewer/PostTest.java), 6개)

## 주요 파일

- [`MainActivity.java`](app/src/main/java/com/example/photoviewer/MainActivity.java) — 동기화/자동 동기화·당겨서 새로고침/업로드/검색/삭제/서버 선택
- [`ImageAdapter.java`](app/src/main/java/com/example/photoviewer/ImageAdapter.java) — RecyclerView 어댑터
- [`Post.java`](app/src/main/java/com/example/photoviewer/Post.java) — 게시물 JSON 모델
- [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml), [`network_security_config.xml`](app/src/main/res/xml/network_security_config.xml) — 강의자료 12p 의 권한 4개(INTERNET, WRITE/READ/MANAGE_EXTERNAL_STORAGE), 평문(HTTP) 통신 허용(13p), 카메라용 FileProvider
- [`activity_main.xml`](app/src/main/res/layout/activity_main.xml), [`item_image.xml`](app/src/main/res/layout/item_image.xml), [`dialog_upload.xml`](app/src/main/res/layout/dialog_upload.xml)
