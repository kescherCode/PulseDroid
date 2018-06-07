package ru.dront78.pulsedroid;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

class BufferSizeAdapter extends BaseAdapter {

    public static final int DEFAULT_INDEX = 4;

    private Context context;
    private List<Integer> presets = Arrays.asList(125, 250, 500, 1000, 2000, 5000, 10000, -1);

    BufferSizeAdapter(Context context) {
        this.context = context;
    }

    @Override
    public int getCount() {
        return presets.size();
    }

    @Override
    public Integer getItem(int position) {
        return presets.get(position);
    }

    @Override
    public long getItemId(int position) {
        return presets.get(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view;
        if (convertView == null) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.simple_text_row, parent, false);
        } else {
            view = convertView;
        }

        TextView contentText = view.findViewById(R.id.contentText);

        int item = getItem(position);
        contentText.setText(formatBufferLength(item));

        return view;
    }

    private String formatBufferLength(int item) {
        if (item < 0) {
            return context.getString(R.string.buffer_infinite);
        }
        DecimalFormat format = new DecimalFormat("");
        double log = Math.log10(item);
        format.setMinimumIntegerDigits(1);
        format.setMaximumFractionDigits(8 - (int) log);
        return format.format(item / 1000d) + "s";
    }

    public int getItemPosition(int size) {
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i) == size) {
                return i;
            }
        }
        return -1;
    }
}
