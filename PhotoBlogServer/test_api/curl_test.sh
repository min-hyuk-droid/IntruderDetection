#!/bin/bash
# 강의자료 ch03 p.33~36 "CLI를 이용한 테스트 - CURL"
# 사용법: ./curl_test.sh [HOST]   (기본값 http://127.0.0.1:8000)
# .env 의 BLOG_USERNAME / BLOG_PASSWORD 사용
set -e
cd "$(dirname "$0")"
source ./.env
HOST=${1:-http://127.0.0.1:8000}

# Windows(Git Bash)의 curl은 명령줄 인자의 한글을 UTF-8이 아닌 시스템 인코딩으로 보내므로
# 한글 값은 UTF-8 파일에 써 두고 -F "name=<파일" 형식으로 전송한다.
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
field() { printf '%s' "$2" > "$TMP/$1"; echo "$1=<$TMP/$1"; }

# [1] ID/Password(Basic 인증)로 게시글 작성 — 비밀번호 노출 위험이 있는 방식
curl -sS -X POST \
  -H 'Accept: application/json' \
  -u "$BLOG_USERNAME:$BLOG_PASSWORD" \
  -F "$(field title '제목')" \
  -F "$(field text 'API 내용')" \
  -F "created_date=2026-10-13T18:34:00+09:00" \
  -F "published_date=2026-10-13T18:34:00+09:00" \
  -F "image=@figure.jpg;type=image/jpg" \
  "$HOST/api_root/Post/"
echo

# [2] 토큰 획득 (서버에서 python manage.py drf_create_token <username> 으로도 발급 가능)
TOKEN=$(curl -s -X POST -d "username=$BLOG_USERNAME" -d "password=$BLOG_PASSWORD" "$HOST/api-token-auth/" \
  | python -c "import sys, json; print(json.load(sys.stdin)['token'])")

# [3] 토큰으로 게시글 작성
curl -sS -X POST \
  -H "Authorization: JWT $TOKEN" \
  -H 'Accept: application/json' \
  -F "$(field title '제목2')" \
  -F "$(field text 'API 내용2')" \
  -F "created_date=2026-10-13T18:34:00+09:00" \
  -F "published_date=2026-10-13T18:34:00+09:00" \
  -F "image=@figure.jpg;type=image/jpg" \
  "$HOST/api_root/Post/"
echo
