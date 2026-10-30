from django.db import models
from django.db.models.signals import post_delete, pre_save
from django.dispatch import receiver
from django.utils import timezone

class Post(models.Model):
    author = models.ForeignKey('auth.User', on_delete=models.CASCADE)
    title = models.CharField(max_length=200)
    text = models.TextField()
    created_date = models.DateTimeField(default=timezone.now)
    published_date = models.DateTimeField(blank=True, null=True)
    image = models.ImageField(upload_to='images/%Y/%m/%d/', default='images/default_error.png')

    def publish(self):
        self.published_date = timezone.now()
        self.save()

    def __str__(self):
        return self.title

DEFAULT_IMAGE = 'images/default_error.png'


def _delete_image_file(image):
    """업로드된 사진 파일 삭제 (모든 글이 함께 쓰는 기본 이미지는 남김)"""
    if image and image.name and image.name != DEFAULT_IMAGE:
        image.storage.delete(image.name)


@receiver(post_delete, sender=Post)
def delete_image_with_post(sender, instance, **kwargs):
    # 글을 지우면 사진 파일도 지움 (지운 글의 사진이 서버에 계속 쌓이지 않게)
    _delete_image_file(instance.image)


@receiver(pre_save, sender=Post)
def delete_replaced_image(sender, instance, **kwargs):
    # 글을 수정하면서 사진을 바꾸면 예전 사진 파일을 지움
    if not instance.pk:
        return
    old = Post.objects.filter(pk=instance.pk).values_list('image', flat=True).first()
    if old and old != instance.image.name:
        _delete_image_file(Post(image=old).image)
