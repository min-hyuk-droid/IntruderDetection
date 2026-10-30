package com.example.photoviewer;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** RecyclerView 를 이용한 이미지 목록 (강의자료 16~17p) + [편의기능] 제목/작성자·게시일, 탭/길게 누르기 */
public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {

    public interface OnPostClickListener {
        void onClick(int position);

        void onLongClick(int position);
    }

    private List<Bitmap> imageList;
    private final List<Post> postList;
    private final OnPostClickListener listener;

    public ImageAdapter(List<Bitmap> imageList, List<Post> postList, OnPostClickListener listener) {
        // 생성자에서 이미지 목록 입력
        this.imageList = imageList;
        this.postList = postList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 이미지 항목을 나타낼 뷰 생성
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        // 해당 위치의 이미지를 뷰에 설정
        Bitmap bitmap = imageList.get(position);
        holder.imageView.setImageBitmap(bitmap);
        Post post = postList.get(position);
        holder.textTitle.setText(post.title);
        holder.textMeta.setText(post.shortDate());  // 카드가 좁아 날짜만, 작성자는 상세보기에서
        holder.itemView.setOnClickListener(v -> listener.onClick(holder.getBindingAdapterPosition()));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(holder.getBindingAdapterPosition());
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return imageList.size();
    }

    public static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView textTitle;
        TextView textMeta;

        public ImageViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageViewItem); // item_image.xml 에 있는 ImageView
            textTitle = itemView.findViewById(R.id.textTitle);
            textMeta = itemView.findViewById(R.id.textMeta);
        }
    }
}
