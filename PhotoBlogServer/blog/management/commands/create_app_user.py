from django.contrib.auth.models import User
from django.core.management.base import BaseCommand
from rest_framework.authtoken.models import Token


class Command(BaseCommand):
    """PhotoViewer 앱 전용 일반 사용자(관리자 아님)와 토큰을 만들고 토큰을 출력한다.

    앱(APK)에는 이 토큰만 넣는다. 관리자 권한이 없으므로 앱에서는 자기 글만 수정·삭제할 수 있다.
    사용법: python manage.py create_app_user [username]   (기본 photoviewer)
    """
    help = 'PhotoViewer 앱 전용 일반 사용자와 토큰 생성 (토큰 출력)'

    def add_arguments(self, parser):
        parser.add_argument('username', nargs='?', default='photoviewer')

    def handle(self, *args, **options):
        user, created = User.objects.get_or_create(username=options['username'])
        user.is_staff = False
        user.is_superuser = False
        user.set_unusable_password()  # 웹 로그인은 막고 토큰으로만 사용
        user.save()
        token, _ = Token.objects.get_or_create(user=user)
        self.stderr.write(f"{'생성' if created else '기존'} 사용자: {user.username} (관리자 아님)")
        self.stdout.write(token.key)
