from django.http import HttpResponseForbidden
from django.shortcuts import render, get_object_or_404, redirect
from django.utils import timezone
from django.contrib.auth.decorators import login_required
from .models import Post
from .forms import PostForm
from rest_framework import viewsets, filters, permissions
from .serializers import PostSerializer
from .permissions import IsAuthorOrReadOnly

def post_list(request):
    posts = Post.objects.filter(
        published_date__lte=timezone.now()
    ).order_by('-published_date')
    return render(request, 'blog/post_list.html', {'posts': posts})

def post_detail(request, pk):
    post = get_object_or_404(Post, pk=pk)
    return render(request, 'blog/post_detail.html', {'post': post})

@login_required
def post_new(request):
    if request.method == "POST":
        form = PostForm(request.POST, request.FILES)
        if form.is_valid():
            post = form.save(commit=False)
            post.author = request.user
            post.published_date = timezone.now()
            post.save()
            return redirect('post_detail', pk=post.pk)
    else:
        form = PostForm()
    return render(request, 'blog/post_edit.html', {'form': form})

@login_required
def post_edit(request, pk):
    post = get_object_or_404(Post, pk=pk)
    # [편의기능] API 와 같은 규칙: 작성자 본인(또는 관리자)만 수정, 작성자는 바뀌지 않음
    if post.author != request.user and not request.user.is_staff:
        return HttpResponseForbidden('작성자만 수정할 수 있습니다.')
    if request.method == "POST":
        form = PostForm(request.POST, request.FILES, instance=post)
        if form.is_valid():
            post = form.save(commit=False)
            post.published_date = timezone.now()
            post.save()
            return redirect('post_detail', pk=post.pk)
    else:
        form = PostForm(instance=post)
    return render(request, 'blog/post_edit.html', {'form': form})

def js_test(request):
    return render(request, 'blog/js_test.html', {})

class BlogImages(viewsets.ModelViewSet):
    # 최신 게시물이 먼저 오도록 정렬 (앱 목록 상단 = 최신 글)
    queryset = Post.objects.all().order_by('-published_date', '-id')
    serializer_class = PostSerializer
    # [편의기능] ?search=키워드 로 제목/본문 검색
    filter_backends = [filters.SearchFilter]
    search_fields = ['title', 'text']
    # [편의기능] 조회는 누구나, 작성은 로그인/토큰 사용자, 수정·삭제는 작성자만
    permission_classes = [permissions.IsAuthenticatedOrReadOnly, IsAuthorOrReadOnly]

    def perform_create(self, serializer):
        # 앱에서 게시일을 보내지 않으면 지금 시각으로 게시 (웹 목록에도 바로 보이도록)
        published = serializer.validated_data.get('published_date') or timezone.now()
        serializer.save(author=self.request.user, published_date=published)
