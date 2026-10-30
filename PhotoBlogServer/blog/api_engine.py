"""포토 블로그 API 검증 엔진

강의자료 ch05 8~10p 의 검증 과정을 코드로 옮긴 것.
  - 8p  telnet 으로 원시 HTTP 요청 전송          -> send_raw_request()
  - 9p  응답(Response Line/Header/Body) 확인      -> parse_http_response()
  - 10p codebeautify 로 JSON Array 형식 검증       -> validate_post_list()

blog/tests.py 의 자동 테스트와 test_api/api_check.py(CLI)가 함께 사용한다.
"""
import json
import socket
from dataclasses import dataclass, field
from urllib.parse import urlparse

# 게시물 1개(JSON Object)가 반드시 가져야 하는 필드와 타입
POST_FIELDS = {
    'id': int,
    'author': int,
    'title': str,
    'text': str,
    'created_date': str,
    'published_date': (str, type(None)),
    'image': str,
}


@dataclass
class HttpResponse:
    version: str
    status: int
    reason: str
    headers: dict = field(default_factory=dict)
    body: str = ''


def build_get_request(host, port, path, token=None, user_agent='PhotoBlog-ApiEngine/1.0'):
    """강의자료 8p 의 telnet 입력과 같은 형식의 GET 요청 메시지를 만든다."""
    lines = [f'GET {path} HTTP/1.1']
    if token:
        lines.append(f'Authorization: Token {token}')
    lines += [
        f'User-Agent: {user_agent}',
        f'Host: {host}:{port}',
        'Accept: application/json',
        'Connection: close',
        '',
        '',
    ]
    return '\r\n'.join(lines).encode('utf-8')


def send_raw_request(host, port, path, token=None, timeout=5):
    """소켓으로 원시 HTTP 요청을 보내고 응답 바이트 전체를 돌려준다 (telnet 과 동일)."""
    request = build_get_request(host, port, path, token)
    with socket.create_connection((host, port), timeout=timeout) as sock:
        sock.sendall(request)
        chunks = []
        while True:
            data = sock.recv(4096)
            if not data:
                break
            chunks.append(data)
    return b''.join(chunks)


def _dechunk(raw):
    """Transfer-Encoding: chunked 본문을 이어 붙인다."""
    out = b''
    while raw:
        size_line, _, raw = raw.partition(b'\r\n')
        size = int(size_line.split(b';')[0] or b'0', 16)
        if size == 0:
            break
        out += raw[:size]
        raw = raw[size + 2:]
    return out


def parse_http_response(raw):
    """Response Line / HTTP Header / Empty Line / Message Body 로 분리한다 (강의자료 9p)."""
    head, sep, body = raw.partition(b'\r\n\r\n')
    if not sep:
        raise ValueError('HTTP 헤더와 본문을 구분하는 빈 줄(Empty Line)이 없습니다.')
    lines = head.decode('iso-8859-1').split('\r\n')
    version, status, reason = (lines[0].split(' ', 2) + ['', ''])[:3]
    if not version.startswith('HTTP/'):
        raise ValueError(f'올바른 Response Line 이 아닙니다: {lines[0]!r}')
    headers = {}
    for line in lines[1:]:
        name, _, value = line.partition(':')
        headers[name.strip().lower()] = value.strip()
    if headers.get('transfer-encoding', '').lower() == 'chunked':
        body = _dechunk(body)
    return HttpResponse(version, int(status), reason, headers, body.decode('utf-8'))


def validate_post(obj, index=0):
    """게시물 JSON Object 1개를 검사해서 오류 메시지 목록을 돌려준다."""
    errors = []
    if not isinstance(obj, dict):
        return [f'[{index}] JSON Object 가 아닙니다: {type(obj).__name__}']
    for name, types in POST_FIELDS.items():
        if name not in obj:
            errors.append(f'[{index}] 필드 누락: {name}')
        elif not isinstance(obj[name], types):
            errors.append(f'[{index}] {name} 타입 오류: {type(obj[name]).__name__}')
    image = obj.get('image')
    if isinstance(image, str) and image:
        url = urlparse(image)
        if url.scheme not in ('http', 'https') or not url.netloc:
            errors.append(f'[{index}] image 가 절대 URL 이 아닙니다: {image}')
    return errors


def validate_post_list(body):
    """GET /api_root/Post/ 의 Message Body 가 게시물 JSON Array 인지 검사한다 (강의자료 10p)."""
    try:
        data = json.loads(body)
    except json.JSONDecodeError as e:
        return [f'JSON 형식 오류: {e}']
    if not isinstance(data, list):
        return [f'JSON Array 가 아닙니다: {type(data).__name__}']
    errors = []
    for i, obj in enumerate(data):
        errors += validate_post(obj, i)
    return errors


def check_server(base_url, token=None):
    """서버에 원시 요청을 보내 응답 전체를 검증하고 (응답, 오류목록)을 돌려준다."""
    url = urlparse(base_url)
    if url.scheme != 'http':
        raise ValueError('원시 소켓 검증은 http:// 서버만 지원합니다.')
    host, port = url.hostname, url.port or 80
    response = parse_http_response(send_raw_request(host, port, '/api_root/Post/', token))
    errors = []
    if response.status != 200:
        errors.append(f'상태 코드 {response.status} {response.reason} (200 OK 기대)')
    if 'application/json' not in response.headers.get('content-type', ''):
        errors.append(f"Content-Type 이 JSON 이 아닙니다: {response.headers.get('content-type')}")
    allow = response.headers.get('allow', '')
    for method in ('GET', 'POST', 'HEAD', 'OPTIONS'):
        if method not in allow:
            errors.append(f'Allow 헤더에 {method} 없음: {allow}')
    if not errors:
        errors += validate_post_list(response.body)
    return response, errors
