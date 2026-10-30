# permission_check.py — [편의기능] "작성자 본인만 수정·삭제" 권한 확인
# 다른 사용자의 토큰으로 남의 게시물을 수정/삭제하면 403, 자기 글은 204 가 나와야 한다.
# 사용법: python test_api/permission_check.py <토큰> [서버주소]
#   토큰 발급: python manage.py drf_create_token <username>
import sys

import requests

token = sys.argv[1]
host = sys.argv[2].rstrip('/') if len(sys.argv) > 2 else 'http://127.0.0.1:8000'
headers = {'Authorization': 'Token ' + token, 'Accept': 'application/json'}
api = host + '/api_root/Post/'

me = requests.get(api, headers=headers)
print(f'GET    {api} -> {me.status_code}')
posts = me.json()

# 1. 내 게시물 하나 작성
res = requests.post(api, headers=headers, data={'title': '권한 테스트', 'text': 'guest 가 쓴 글'},
                    files={'image': ('t.jpg', open(__file__.replace('permission_check.py', 'figure.jpg'), 'rb'))})
mine = res.json()
print(f"POST   {api} -> {res.status_code} (id={mine['id']}, author_name={mine['author_name']})")

# 2. 다른 사람 게시물 수정/삭제 시도 -> 403
other = next(p for p in posts if p['author_name'] != mine['author_name'])
url = f"{api}{other['id']}/"
res = requests.patch(url, headers=headers, data={'title': '남의 글 수정 시도'})
print(f"PATCH  {url} (작성자 {other['author_name']}) -> {res.status_code} {res.json()}")
res = requests.delete(url, headers=headers)
print(f"DELETE {url} (작성자 {other['author_name']}) -> {res.status_code} {res.json()}")

# 3. 내 게시물 삭제 -> 204
url = f"{api}{mine['id']}/"
res = requests.delete(url, headers=headers)
print(f"DELETE {url} (작성자 {mine['author_name']}) -> {res.status_code} No Content")
