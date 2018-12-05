package flamenco.flamenco.ListsFragment;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import java.util.ArrayList;

import flamenco.flamenco.ListMusic;
import flamenco.flamenco.MainFragment.SongAdapter;
import flamenco.flamenco.R;
import flamenco.flamenco.Song;

public class QueueFragment extends Fragment{

    private ArrayList<Song> songList;
    private ListView songView;
    public ListMusic listMusic;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        View view =  inflater.inflate(R.layout.queuefragment, container, false);
        songView = view.findViewById(R.id.queueList);
        listMusic = (ListMusic) getActivity();
        songList = listMusic.songList;

        return  view;
    }

    public void refreshQueue() {
        songList = listMusic.getServiceList();
        updateCurrentSong(false);
    }

    public void updateCurrentSong(final boolean selected) {

        int index = songView.getFirstVisiblePosition();
        View v = songView.getChildAt(0);
        int top = (v == null) ? 0 : v.getTop();


        songView.setAdapter(new SongAdapter(getActivity(), songList, "song") {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                View bar = row.findViewById(R.id.imageView6);

                if (songList.get(position).getTitle().equals(((ListMusic) getActivity()).getCurrSong().getTitle())
                        && songList.get(position).getArtist().equals(((ListMusic) getActivity()).getCurrSong().getArtist())) {
                    ObjectAnimator animation = ObjectAnimator.ofFloat(bar,
                            "scaleX", 0, 1);
                    animation.setDuration(200);
                    animation.setStartDelay(100);
                    animation.start();
                }  else if (songList.get(position).getJustPlayed()) {
                    bar.setScaleX(1);
                    ObjectAnimator animation = ObjectAnimator.ofFloat(bar,
                            "scaleX", 1, 0);
                    animation.setDuration(200);
                    animation.start();
                    listMusic.songList.get(position).setJustPlayed(false);

                } else {
                    bar.setScaleX(0);
                }

                return row;
            }
        });

        songView.setSelectionFromTop(index, top);
    }

}