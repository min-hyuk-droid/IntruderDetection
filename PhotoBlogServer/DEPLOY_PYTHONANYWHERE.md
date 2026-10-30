# PythonAnywhere 배포 기록

배포 주소: **https://sungmh.pythonanywhere.com** (Beginner 무료 계정, 2026-10-30 배포)

아래는 실제로 수행한 절차다.

## 1. Bash 콘솔 (Consoles → Bash)

```bash
git clone https://github.com/min-hyuk-droid/IntruderDetection.git
cd ~/IntruderDetection/PhotoBlogServer
python3.13 -m venv ~/.virtualenvs/photoblog-venv
source ~/.virtualenvs/photoblog-venv/bin/activate
pip install -r requirements.txt
python manage.py migrate
python manage.py collectstatic --noinput

# 관리자 계정 (비밀번호 로그인은 막아 둠)
python manage.py shell -c "from django.contrib.auth.models import User; u,_=User.objects.get_or_create(username='admin', defaults={'is_staff': True, 'is_superuser': True}); u.set_unusable_password(); u.save()"
# 웹 관리자 로그인이 필요하면: python manage.py changepassword admin
```

## 2. Web 탭 → Add a new web app

1. 도메인 화면에서 **Next**
2. **Manual configuration** → **Python 3.13** → **Next**
3. 생성된 웹앱 설정

| 항목 | 값 |
|---|---|
| Source code | `/home/sungmh/IntruderDetection/PhotoBlogServer` |
| Virtualenv | `/home/sungmh/.virtualenvs/photoblog-venv` |
| Static files `/static/` | `/home/sungmh/IntruderDetection/PhotoBlogServer/staticfiles` |
| Static files `/media/` | `/home/sungmh/IntruderDetection/PhotoBlogServer/media` |

## 3. WSGI 설정 파일 (`/var/www/sungmh_pythonanywhere_com_wsgi.py`)

```python
import os, sys
os.environ['DJANGO_DEBUG'] = 'False'                 # 배포 서버는 오류 상세 화면을 노출하지 않음
os.environ['DJANGO_SECRET_KEY'] = '<서버에서 생성한 비밀키>'  # git 에 올리지 않음
path = '/home/sungmh/IntruderDetection/PhotoBlogServer'
if path not in sys.path:
    sys.path.insert(0, path)
os.environ['DJANGO_SETTINGS_MODULE'] = 'mysite.settings'
from django.core.wsgi import get_wsgi_application
application = get_wsgi_application()
```

비밀키는 PythonAnywhere 콘솔에서 `python3 -c 'import secrets; print(secrets.token_urlsafe(50))'` 로 만들어 WSGI 파일에만 넣었다.

## 4. 배포 스크립트 → Web 탭 Reload

```bash
bash ~/IntruderDetection/PhotoBlogServer/deploy_pythonanywhere.sh
```

최신 코드·라이브러리·DB·정적 파일을 반영하고, 토큰 두 개를 출력한 뒤 웹앱을 다시 불러온다.

| 사용자 | 토큰을 넣는 곳 |
|---|---|
| `photoviewer` (앱 전용, 관리자 아님) | PhotoViewer `secrets.properties` → `REMOTE_TOKEN` |
| `yolo` (YOLOv5 전용, 관리자 아님) | YOLOv5 `.env` → `BLOG_TOKEN` (`BLOG_HOST=https://sungmh.pythonanywhere.com`) |

Static files 매핑을 처음 추가했을 때는 Web 탭의 **Reload** 버튼을 한 번 눌러야 `/static/`, `/media/` 가 열린다.

> 무료 계정은 한 달에 한 번 Web 탭의 **Run until 1 month from today** 를 눌러야 사이트가 유지된다.
