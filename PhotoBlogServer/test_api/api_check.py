# api_check.py — 실행 중인 서버를 API 검증 엔진(blog/api_engine.py)으로 점검
# 사용법:
#   python test_api/api_check.py                       # http://127.0.0.1:8000
#   python test_api/api_check.py http://<아이디>.pythonanywhere.com
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from blog import api_engine  # noqa: E402

base_url = sys.argv[1] if len(sys.argv) > 1 else 'http://127.0.0.1:8000'
response, errors = api_engine.check_server(base_url)

print(f'{response.version} {response.status} {response.reason}')
for name in ('allow', 'content-type'):
    print(f'{name}: {response.headers.get(name)}')
if response.status == 200:
    print(f'게시물 {len(json.loads(response.body))}개')
print('검증 결과:', 'PASS' if not errors else 'FAIL')
for e in errors:
    print('  -', e)
sys.exit(1 if errors else 0)
