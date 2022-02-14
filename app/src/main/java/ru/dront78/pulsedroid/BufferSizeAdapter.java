package ru.dront78.pulsedroid;

import android.content.Context;

import java.text.DecimalFormat;
import java.util.List;

class BufferSizeAdapter extends SimpleRowAdapter {
    private final Context context;

    BufferSizeAdapter(Context context, List<Integer> presets) {
        super(presets);
        this.context = context;
    }

    @Override
    protected String formatEntry(int item) {
        if (item < 0) {
            return context.getString(R.string.buffer_infinite);
        }
        if (item == 0) {
            return context.getString(R.string.buffer_minimal);
        }
        DecimalFormat format = new DecimalFormat("");
        format.setMinimumIntegerDigits(1);
        format.setMaximumFractionDigits(3);
        return format.format(item / 1000d) + "s";
    }
}
