package com.soorinote.einknote;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.soorinote.einknote.databinding.ItemNoteBinding;
import java.util.List;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.ViewHolder> {
    public interface OnNoteListener {
        void onEdit(Note note);
        void onDelete(Note note);
 //       void onGoogleSave(Note note);
    }

    private List<Note> list;
    private final OnNoteListener listener;

    public NoteAdapter(List<Note> list, OnNoteListener listener) {
        this.list = list;
        this.listener = listener;
    }

    public void updateData(List<Note> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemNoteBinding binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Note note = list.get(position);
        holder.binding.tvTitle.setText(note.title);
        holder.binding.tvDate.setText(note.updatedAt);
        holder.binding.btnEdit.setOnClickListener(v -> listener.onEdit(note));
        holder.binding.btnDelete.setOnClickListener(v -> listener.onDelete(note));
//        holder.binding.btnGoogleSync.setOnClickListener(v -> listener.onGoogleSave(note));
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ItemNoteBinding binding;
        ViewHolder(ItemNoteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}