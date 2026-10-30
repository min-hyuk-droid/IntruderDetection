from rest_framework.authentication import TokenAuthentication


class JWTKeywordTokenAuthentication(TokenAuthentication):
    """강의자료의 'Authorization: JWT <token>' 헤더를 그대로 쓸 수 있도록
    DRF 토큰 인증의 키워드를 'JWT'로 바꾼 클래스 (토큰 자체는 DRF authtoken)."""
    keyword = 'JWT'
