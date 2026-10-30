# test_api.py — 강의자료 ch03 p.37~38 "Python code를 이용한 테스트"
# 사용법:
#   python test_api.py                                  # 로컬 서버(http://127.0.0.1:8000)
#   python test_api.py https://<아이디>.pythonanywhere.com   # 배포 서버
# 계정은 환경변수 BLOG_USERNAME / BLOG_PASSWORD 또는 같은 폴더의 .env 파일에서 읽는다.
import os
import sys
from pathlib import Path

import requests  # 오류가 있으면 "pip install requests"

HERE = Path(__file__).resolve().parent
HOST = sys.argv[1].rstrip('/') if len(sys.argv) > 1 else 'http://127.0.0.1:8000'


def load_env():
    env_file = HERE / '.env'
    if env_file.exists():
        for line in env_file.read_text(encoding='utf-8').splitlines():
            if '=' in line and not line.startswith('#'):
                key, value = line.split('=', 1)
                os.environ.setdefault(key.strip(), value.strip())


load_env()

# 1. 토큰 획득
res = requests.post(HOST + '/api-token-auth/', {
    'username': os.environ['BLOG_USERNAME'],
    'password': os.environ['BLOG_PASSWORD'],
})
res.raise_for_status()
token = res.json()['token']
print('token 획득 완료')

# 인증이 필요한 요청에 아래의 headers를 붙임
headers = {'Authorization': 'JWT ' + token, 'Accept': 'application/json'}

# 2. Post Create (이미지 포함, multipart/form-data)
data = {
    'title': '제목 by code',
    'text': 'API내용 by code',
    'created_date': '2026-10-13T18:34:00+09:00',
    'published_date': '2026-10-13T18:34:00+09:00',
}
with open(HERE / 'figure.jpg', 'rb') as image:
    res = requests.post(HOST + '/api_root/Post/', data=data,
                        files={'image': image}, headers=headers)
print(res)
print(res.json())
