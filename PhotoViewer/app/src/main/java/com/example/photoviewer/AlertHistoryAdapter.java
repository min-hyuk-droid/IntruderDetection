package com.example.photoviewer;

import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** [편의기능] 침입 기록 목록: 기기에 저장된 사진·물체·시각 (서버 연결 없이 표시) */
class AlertHistoryAdapter extends RecyclerView.Adapter<AlertHistoryAdapter.Holder> {
    private final List<AlertRecord> records;
    private final Function<String, String> serverName;
    private final Consumer<AlertRecord> onClick;

    AlertHistoryAdapter(List<AlertRecord> records, Function<String, String> serverName, Consumer<AlertRecord> onClick) {
        this.records = records;
        this.serverName = serverName;
        this.onClick = onClick;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alert, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        AlertRecord record = records.get(position);
        holder.image.setImageBitmap(record.imagePath == null ? null : BitmapFactory.decodeFile(record.imagePath));
        holder.title.setText(record.heading());
        holder.meta.setText(record.meta(serverName.apply(record.server)));
        holder.objects.setText(record.objects);
        holder.itemView.setOnClickListener(v -> onClick.accept(record));
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title;
        final TextView meta;
        final TextView objects;

        Holder(View view) {
            super(view);
            image = view.findViewById(R.id.imageAlert);
            title = view.findViewById(R.id.textAlertTitle);
            meta = view.findViewById(R.id.textAlertMeta);
            objects = view.findViewById(R.id.textAlertObjects);
        }
    }
}
