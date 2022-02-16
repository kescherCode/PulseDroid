package at.kescher.pulsedroid;

import android.content.Context;

import java.util.List;

public class ChannelAdapter extends SimpleRowAdapter {
    private final Context context;

    ChannelAdapter(Context context, List<Integer> presets) {
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
