# PhotoBlogServer — Django 이미지 블로그 서버

강의자료 ch08 침입자 감시 시스템의 서버(Django + Django REST framework). YOLOv5 Edge 가 올린 검출 이미지를 웹과 앱으로 보여준다.
ch03 이미지 블로그([web_blog](https://github.com/min-hyuk-droid/web_blog)) 코드를 이어받아 확장했다.

## 실행

```bash
python -m venv venv
venv\Scripts\activate              # macOS/Linux: source venv/bin/activate
pip install -r requirements.txt -r requirements-dev.txt
python manage.py migrate
python manage.py createsuperuser
python manage.py create_app_user yolo          # YOLOv5 전용 사용자 토큰 출력 (YOLOv5/.env 의 BLOG_TOKEN)
python manage.py create_app_user               # 앱 전용 사용자 photoviewer 생성
python manage.py changepassword photoviewer    # 앱에서 로그인할 비밀번호
python manage.py runserver
```

| 화면 | URL |
|---|---|
| 웹 블로그 | http://127.0.0.1:8000/ |
| API Root | http://127.0.0.1:8000/api_root/ |
| 게시물 API | http://127.0.0.1:8000/api_root/Post/ |
| 토큰 발급 | `POST /api-token-auth/` (username, password). 같은 IP 에서 분당 5회까지, 초과하면 429 *(편의기능 보안)* |

`ALLOWED_HOSTS` 에 `127.0.0.1`, `localhost`, `10.0.2.2`(Android 에뮬레이터), `.pythonanywhere.com` 이 등록되어 있다.

## API (`/api_root/Post/`)

| 메소드 | 설명 | 인증 |
|---|---|---|
| `GET` | 게시물 목록 — **JSON Array**, 최신 글 먼저, 이미지 절대 URL 포함 | 불필요 |
| `GET ?search=키워드` | 제목/본문 검색 *(편의기능)* | 불필요 |
| `POST` | 게시물 작성 — Multipart(Text + Image). 작성자는 토큰 사용자, 게시일 생략 시 현재 시각. 사진이 아니거나 5MB 초과면 400 *(편의기능 보안)* | 필요 |
| `PUT/PATCH/DELETE /{id}/` | 수정/삭제 — **작성자 본인(또는 관리자)만** *(편의기능)*, 그 외 403. 웹 화면 글 수정도 같은 규칙 | 필요 |
| `HEAD`, `OPTIONS` | 목록 응답의 `Allow: GET, POST, HEAD, OPTIONS` | — |

인증 헤더는 `Authorization: Token <token>` 과 강의자료 형식 `Authorization: JWT <token>` 모두 허용.

## 테스트

| 파일 | 내용 |
|---|---|
| [`blog/api_engine.py`](blog/api_engine.py) | **API 검증 엔진** — telnet 과 같은 원시 HTTP 요청 생성·전송, Response Line/Header/Body 파싱, 게시물 JSON Array 스키마 검증 (강의자료 8~10p 를 코드로) |
| [`blog/tests.py`](blog/tests.py) | Django 자동 테스트 41개 — GET/POST/검색/권한/토큰/웹 화면/엔진 단위 테스트/실서버 원시 소켓 테스트/사진 파일 정리 |
| [`test_api/api_check.py`](test_api/api_check.py) | 실행 중인 서버(로컬·PythonAnywhere)를 엔진으로 점검하는 CLI |
| [`socket_server/`](socket_server) | 강의자료 ch05 24p / ch03-02 30~31p Socket Server — 앱 요청을 `request/년-월-일-시-분-초.bin` 으로 저장, multipart 이미지 추출, `response.bin` 응답 |
| [`test_api/permission_check.py`](test_api/permission_check.py) | 남의 글 수정/삭제 403, 내 글 삭제 204 확인 |
| [`test_api/test_api.py`](test_api/test_api.py), [`curl_test.sh`](test_api/curl_test.sh) | ch03 토큰 발급·게시 테스트 |

```bash
python manage.py test blog                    # 자동 테스트
coverage run manage.py test blog && coverage report && coverage html   # htmlcov/index.html (tests.py 포함 100%)
python test_api/api_check.py                  # 실행 중인 서버 검증
python test_api/api_check.py http://<아이디>.pythonanywhere.com
```

## PythonAnywhere 배포

[`DEPLOY_PYTHONANYWHERE.md`](DEPLOY_PYTHONANYWHERE.md) 참고.
