from blog.models import Post
from rest_framework import serializers


MAX_IMAGE_BYTES = 5 * 1024 * 1024


class PostSerializer(serializers.ModelSerializer):
    # 작성자는 요청한 사용자(토큰/로그인)로 자동 지정
    author = serializers.PrimaryKeyRelatedField(read_only=True)
    author_name = serializers.CharField(source='author.username', read_only=True)

    class Meta:
        model = Post
        fields = ('id', 'author', 'author_name', 'title', 'text',
                  'created_date', 'published_date', 'image')

    def validate_image(self, image):
        # [보안] 큰 파일로 서버 저장 공간을 채우지 못하게 제한 (사진이 맞는지는 ImageField 가 Pillow 로 검사)
        if image.size > MAX_IMAGE_BYTES:
            raise serializers.ValidationError('이미지는 5MB 이하만 올릴 수 있습니다.')
        return image
