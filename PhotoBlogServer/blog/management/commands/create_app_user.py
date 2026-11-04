from django.contrib.auth.models import User
from django.core.management.base import BaseCommand
from rest_framework.authtoken.models import Token


class Command(BaseCommand):
    """앱·YOLOv5 전용 일반 사용자(관리자 아님)와 토큰을 만들고 토큰을 출력한다.

    관리자 권한이 없으므로 자기 글만 수정·삭제할 수 있다.
    앱에서 로그인할 사용자는 비밀번호를 정한다: python manage.py changepassword <username>
    사용법: python manage.py create_app_user [username]   (기본 photoviewer)
    """
    help = 'PhotoViewer 앱 전용 일반 사용자와 토큰 생성 (토큰 출력)'

    def add_arguments(self, parser):
        parser.add_argument('username', nargs='?', default='photoviewer')

    def handle(self, *args, **options):
        user, created = User.objects.get_or_create(username=options['username'])
        user.is_staff = False
        user.is_superuser = False
        if created:  # 새 사용자는 비밀번호 로그인을 막아 둠 (이미 정한 비밀번호는 배포 때 지우지 않음)
            user.set_unusable_password()
        user.save()
        token, _ = Token.objects.get_or_create(user=user)
        self.stderr.write(f"{'생성' if created else '기존'} 사용자: {user.username} (관리자 아님)")
        self.stdout.write(token.key)
