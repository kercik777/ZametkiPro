package com.zametki.pro.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.zametki.pro.R;

public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.VH> {

    private final int[] titles = {
            R.string.onb1_title, R.string.onb2_title, R.string.onb3_title, R.string.onb4_title
    };
    private final int[] descs = {
            R.string.onb1_desc, R.string.onb2_desc, R.string.onb3_desc, R.string.onb4_desc
    };
    private final int[] icons = {
            R.drawable.ic_logo, R.drawable.ic_category, R.drawable.ic_check_box_outline, R.drawable.ic_info
    };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_onboarding, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        h.title.setText(titles[pos]);
        h.desc.setText(descs[pos]);
        h.image.setImageResource(icons[pos]);
        if (pos != 0) {
            h.image.setColorFilter(androidx.core.content.ContextCompat.getColor(
                    h.itemView.getContext(), R.color.gold_primary));
        } else {
            h.image.clearColorFilter();
        }
    }

    @Override
    public int getItemCount() { return titles.length; }

    static class VH extends RecyclerView.ViewHolder {
        ImageView image;
        TextView title, desc;
        VH(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.iv_image);
            title = v.findViewById(R.id.tv_title);
            desc = v.findViewById(R.id.tv_desc);
        }
    }
}
