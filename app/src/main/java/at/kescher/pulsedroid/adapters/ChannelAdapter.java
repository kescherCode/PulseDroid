package at.kescher.pulsedroid.adapters;

import android.content.Context;

import java.util.List;

import at.kescher.pulsedroid.R;

public class ChannelAdapter extends SimpleRowAdapter {
    private final Context context;

    public ChannelAdapter(Context context, List<Integer> presets) {
        super(presets);
        this.context = context;
    }

    @Override
    protected String formatEntry(int item) {
        if (item == 1) {
            return context.getString(R.string.Mono);
        } else if (item == 2) {
            return context.getString(R.string.Stereo);
        }
        return Integer.toString(item);
    }
}
