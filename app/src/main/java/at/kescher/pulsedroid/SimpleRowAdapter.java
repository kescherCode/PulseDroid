package at.kescher.pulsedroid;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class SimpleRowAdapter extends BaseAdapter {
    private final List<Integer> presets;

    SimpleRowAdapter(List<Integer> presets) {
        this.presets = presets;
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
        contentText.setText(formatEntry(item));

        return view;
    }

    protected String formatEntry(int item) {
        return Integer.toString(item);
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
