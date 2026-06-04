package com.focus.browser;

import android.content.res.Configuration;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class SuggestionAdapter extends RecyclerView.Adapter<SuggestionAdapter.ViewHolder> {
    public interface OnSuggestionClick { void onSuggestionClicked(String suggestion); }
    private final OnSuggestionClick listener;
    private final int textColor;
    private List<String> suggestions = new ArrayList<>();

    public SuggestionAdapter(View parentView, OnSuggestionClick listener) {
        this.listener = listener;
        boolean isDark = (parentView.getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        this.textColor = isDark ? Color.WHITE : Color.BLACK;
    }

    public void setSuggestions(List<String> newList) {
        suggestions = newList;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_suggestion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.textView.setText(suggestions.get(position));
        holder.textView.setTextColor(textColor);
        holder.itemView.setOnClickListener(v -> listener.onSuggestionClicked(suggestions.get(position)));
    }

    @Override
    public int getItemCount() { return suggestions.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        ViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.text1);
        }
    }
}