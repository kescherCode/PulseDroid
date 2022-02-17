package at.kescher.pulsedroid.adapters;

import android.content.Context;

import java.text.DecimalFormat;
import java.util.List;

import at.kescher.pulsedroid.R;

public class DurationAdapter extends SimpleRowAdapter {
    private final Context context;

    public DurationAdapter(Context context, List<Integer> presets) {
        super(presets);
        this.context = context;
    }

    @Override
    protected String formatEntry(int item) {
        if (item < 0) {
            return context.getString(R.string.infinite);
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
