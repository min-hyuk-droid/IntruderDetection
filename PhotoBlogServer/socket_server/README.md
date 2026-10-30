# socket_server

강의자료 ch05 24p 「Socket Server를 이용한 Client Request Protocol 검증」.
코드는 강의자료 ch03-02 30p 의 `SocketServer` 뼈대와 `response.bin` 을 그대로 쓰고,
`# 여기에 구현 하세요` 부분에 31p 실습 내용을 구현했다.

- 실습 1: 클라이언트 요청을 그대로 `request/<년-월-일-시-분-초>.bin` 으로 저장
- 실습 2: multipart/form-data 로 받은 이미지를 `images/<년-월-일-시-분-초>_<파일명>` 으로 저장
- 응답: `response.bin` 내용을 그대로 전송

```bash
# Django runserver 를 끈 뒤 (같은 8000 포트 사용)
cd socket_server
python socket_server.py        # Ctrl+C 로 종료
```

PhotoViewer 앱에서 [동기화] 를 누르면 요청 메시지가 `.bin` 으로 저장되고, VSCode 로 열어 확인한다.
`request/*.bin`, `images/` 에는 토큰·업로드 이미지가 들어 있으므로 git 에는 올리지 않는다.
