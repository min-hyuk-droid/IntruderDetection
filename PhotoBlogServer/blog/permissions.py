from rest_framework import permissions


class IsAuthorOrReadOnly(permissions.BasePermission):
    """조회는 누구나, 수정/삭제는 게시물 작성자(또는 관리자)만 허용"""

    def has_object_permission(self, request, view, obj):
        if request.method in permissions.SAFE_METHODS:
            return True
        return obj.author == request.user or request.user.is_staff
