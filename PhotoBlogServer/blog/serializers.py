from blog.models import Post
from rest_framework import serializers


class PostSerializer(serializers.ModelSerializer):
    # 작성자는 요청한 사용자(토큰/로그인)로 자동 지정
    author = serializers.PrimaryKeyRelatedField(read_only=True)
    author_name = serializers.CharField(source='author.username', read_only=True)

    class Meta:
        model = Post
        fields = ('id', 'author', 'author_name', 'title', 'text',
                  'created_date', 'published_date', 'image')
