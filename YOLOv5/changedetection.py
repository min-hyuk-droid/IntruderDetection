import os
import cv2
import pathlib
import requests
from datetime import datetime


def load_env(path=pathlib.Path(__file__).resolve().parent / '.env'):
    """YOLOv5/.env 의 BLOG_HOST, BLOG_TOKEN (또는 BLOG_USERNAME, BLOG_PASSWORD) 를 읽는다.
    서버 주소·토큰·비밀번호를 코드에 적지 않고 git 에 올리지 않기 위함 (.env 는 .gitignore 에 있음)."""
    if path.exists():
        for line in path.read_text(encoding='utf-8').splitlines():
            if '=' in line and not line.strip().startswith('#'):
                key, value = line.split('=', 1)
                os.environ.setdefault(key.strip(), value.strip())


class ChangeDetection:
    result_prev = []
    HOST = 'http://127.0.0.1:8000'
    username = 'admin'
    password = ''
    token = ''
    title = ''
    text = ''

    def __init__(self, names):
        load_env()
        self.HOST = os.environ.get('BLOG_HOST', self.HOST).rstrip('/')
        self.username = os.environ.get('BLOG_USERNAME', self.username)
        self.password = os.environ.get('BLOG_PASSWORD', self.password)
        self.result_prev = [0 for i in range(len(names))]

        self.token = os.environ.get('BLOG_TOKEN', '')
        if not self.token:  # 토큰이 없으면 강의자료처럼 아이디/비밀번호로 토큰 발급
            res = requests.post(self.HOST + '/api-token-auth/', {
                'username': self.username,
                'password': self.password,
            }, timeout=10)
            res.raise_for_status()
            self.token = res.json()['token']  # 토큰 저장
        print('ChangeDetection:', self.HOST)

    def add(self, names, detected_current, save_dir, image):
        self.title = ''
        self.text = ''
        change_flag = 0  # 변화 감지 플래그
        i = 0
        while i < len(self.result_prev):
            if self.result_prev[i] == 0 and detected_current[i] == 1:
                change_flag = 1
                self.title = names[i]
                self.text += names[i] + ", "
            i += 1

        self.result_prev = detected_current[:]  # 객체 검출 상태 저장

        if change_flag == 1:
            self.send(save_dir, image)

    def send(self, save_dir, image):
        now = datetime.now()
        now.isoformat()

        today = datetime.now()
        save_path = os.getcwd() / save_dir / 'detected' / str(today.year) / str(today.month) / str(today.day)
        pathlib.Path(save_path).mkdir(parents=True, exist_ok=True)

        full_path = save_path / '{0}-{1}-{2}-{3}.jpg'.format(today.hour, today.minute, today.second, today.microsecond)

        dst = cv2.resize(image, dsize=(320, 240), interpolation=cv2.INTER_AREA)
        cv2.imwrite(str(full_path), dst)

        # 인증이 필요한 요청에 아래의 headers 를 붙임
        headers = {'Authorization': 'JWT ' + self.token, 'Accept': 'application/json'}

        # Post Create
        data = {
            'title': self.title,
            'text': self.text,
            'created_date': now,
            'published_date': now
        }
        try:
            with open(full_path, 'rb') as image_file:
                file = {'image': image_file}
                res = requests.post(self.HOST + '/api_root/Post/', data=data, files=file, headers=headers, timeout=10)
            print(res)
        except requests.RequestException as e:  # 서버가 꺼져 있어도 검출은 계속
            print('send failed:', e)
