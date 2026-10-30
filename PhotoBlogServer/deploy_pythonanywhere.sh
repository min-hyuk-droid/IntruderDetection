#!/bin/bash
# PythonAnywhere Bash 콘솔에서 실행:  bash ~/IntruderDetection/PhotoBlogServer/deploy_pythonanywhere.sh
# 최신 코드 받기 → 라이브러리 → DB → 정적 파일 → 앱·YOLO 전용 사용자 토큰 → 웹앱 재시작
set -e
cd "$(dirname "$0")/.."
git pull --ff-only
source ~/.virtualenvs/photoblog-venv/bin/activate
cd PhotoBlogServer
pip install -q -r requirements.txt
python manage.py migrate
python manage.py collectstatic --noinput | tail -1
echo "앱 전용 사용자 토큰 (PhotoViewer secrets.properties 의 REMOTE_TOKEN):"
python manage.py create_app_user
echo "YOLOv5 전용 사용자 토큰 (YOLOv5/.env 의 BLOG_TOKEN):"
python manage.py create_app_user yolo
# WSGI 파일의 수정 시각을 바꾸면 PythonAnywhere 가 웹앱을 다시 불러온다 (Web 탭 Reload 와 같음)
touch /var/www/${USER}_pythonanywhere_com_wsgi.py
git log --oneline -1
echo DEPLOY_OK
