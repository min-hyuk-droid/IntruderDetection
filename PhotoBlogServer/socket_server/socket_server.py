import os
import socket
from datetime import datetime


class SocketServer:
    def __init__(self):
        self.bufsize = 1024  # 버퍼 크기 설정
        with open('./response.bin', 'rb') as file:
            self.RESPONSE = file.read()  # 응답 파일 읽기

        self.DIR_PATH = './request'
        self.createDir(self.DIR_PATH)
        self.IMAGE_PATH = './images'  # 실습 2 이미지 저장 폴더 (이미지를 받을 때 생성)

    def createDir(self, path):
        """디렉토리 생성"""
        try:
            if not os.path.exists(path):
                os.makedirs(path)
        except OSError:
            print("Error: Failed to create the directory.")

    def parseHeaders(self, header_bytes):
        """요청 헤더를 {소문자 이름: 값} 으로 변환 (첫 줄 Request Line 제외)"""
        headers = {}
        for line in header_bytes.split(b"\r\n")[1:]:
            name, _, value = line.decode("utf-8", errors="replace").partition(":")
            headers[name.strip().lower()] = value.strip()
        return headers

    def isComplete(self, data):
        """헤더 끝(빈 줄)까지 받았고, Content-Length 만큼 Message Body 도 받았는지 확인"""
        if b"\r\n\r\n" not in data:
            return False
        header_bytes, body = data.split(b"\r\n\r\n", 1)
        length = int(self.parseHeaders(header_bytes).get("content-length", 0))
        return len(body) >= length

    def saveImages(self, data, timestamp):
        """실습 2: multipart/form-data 로 받은 이미지 데이터를 별도 이미지 파일로 저장"""
        header_bytes, body = data.split(b"\r\n\r\n", 1)
        content_type = self.parseHeaders(header_bytes).get("content-type", "")
        if "multipart/form-data" not in content_type or "boundary=" not in content_type:
            return []
        boundary = content_type.split("boundary=", 1)[1].strip().strip('"')
        delimiter = b"--" + boundary.encode()

        saved = []
        # 각 파트: "--boundary\r\n<파트 헤더>\r\n\r\n<데이터>\r\n", 마지막은 "--boundary--"
        for part in body.split(delimiter)[1:]:
            if part.startswith(b"--"):
                break
            part_header, _, part_data = part.lstrip(b"\r\n").partition(b"\r\n\r\n")
            if part_data.endswith(b"\r\n"):
                part_data = part_data[:-2]
            part_header = part_header.decode("utf-8", errors="replace")
            if "filename=" not in part_header or "image/" not in part_header.lower():
                continue
            filename = part_header.split('filename="', 1)[1].split('"', 1)[0]
            filename = os.path.basename(filename) or "image.bin"
            self.createDir(self.IMAGE_PATH)
            path = os.path.join(self.IMAGE_PATH, timestamp + "_" + filename)
            with open(path, "wb") as f:
                f.write(part_data)
            saved.append(path)
        return saved

    def run(self, ip, port):
        """서버 실행"""
        # 소켓 생성
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind((ip, port))
        self.sock.listen(10)
        print("Start the socket server...")
        print("\"Ctrl+C\" for stopping the server!\r\n")

        try:
            while True:
                # 클라이언트의 요청 대기
                clnt_sock, req_addr = self.sock.accept()
                clnt_sock.settimeout(5.0)  # 타임아웃 설정(5초)
                print("Request message...\r\n")

                response = b""
                # 여기에 구현 하세요
                # 실습 1: 클라이언트 요청을 그대로 받아 "request" 폴더에 "년-월-일-시-분-초.bin" 으로 저장
                continued = False
                try:
                    while not self.isComplete(response):
                        data = clnt_sock.recv(self.bufsize)
                        if not data:
                            break
                        response += data
                        # curl 은 큰 Body 를 보내기 전에 "Expect: 100-continue" 를 보내고 서버 응답을 기다림
                        if not continued and b"\r\n\r\n" in response:
                            headers = self.parseHeaders(response.split(b"\r\n\r\n", 1)[0])
                            if headers.get("expect", "").lower() == "100-continue":
                                clnt_sock.sendall(b"HTTP/1.1 100 Continue\r\n\r\n")
                            continued = True
                except socket.timeout:
                    pass

                if response:
                    timestamp = datetime.now().strftime("%Y-%m-%d-%H-%M-%S")
                    path = os.path.join(self.DIR_PATH, timestamp + ".bin")
                    with open(path, "wb") as f:
                        f.write(response)
                    print(response.decode("utf-8", errors="replace"))
                    print("저장: " + path)
                    # 실습 2: 멀티파트로 전송 받은 이미지 데이터 저장
                    for image_path in self.saveImages(response, timestamp):
                        print("이미지 저장: " + image_path)
                    print()

                # 응답 전송
                clnt_sock.sendall(self.RESPONSE)

                # 클라이언트 소켓 닫기
                clnt_sock.close()
        except KeyboardInterrupt:
            print("\r\nStop the server...")

        # 서버 소켓 닫기
        self.sock.close()


if __name__ == "__main__":
    server = SocketServer()
    server.run("127.0.0.1", 8000)
