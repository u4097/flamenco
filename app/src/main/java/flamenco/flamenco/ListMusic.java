package flamenco.flamenco;

import android.util.Log;
import android.Manifest;
import android.animation.Animator;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Path;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.os.IBinder;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.view.View;
import android.view.MotionEvent;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.FragmentManager;

import flamenco.flamenco.ListsFragment.AddSongToPlaylist;
import flamenco.flamenco.ListsFragment.QueueFragment;
import flamenco.flamenco.MainFragment.AlbumAdapter;
import flamenco.flamenco.MainFragment.AlbumsFragment;
import flamenco.flamenco.MainFragment.FoldersAdapter;
import flamenco.flamenco.MainFragment.MainFragmentAdapter;
import flamenco.flamenco.MainFragment.SongAdapter;
import flamenco.flamenco.MainFragment.SongsFragment;
import flamenco.flamenco.MusicService.MusicBinder;
import android.widget.MediaController.MediaPlayerControl;
import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class ListMusic extends AppCompatActivity implements MediaPlayerControl {

    public ArrayList<flamenco.flamenco.Song> songList;
    public ArrayList<flamenco.flamenco.Song> artistList;
    public ArrayList<flamenco.flamenco.Song> albumList;
    public ArrayList<flamenco.flamenco.Folder> folderList;
    public ArrayList<Song> playList;
    public Folder lastFolder;
    private ArrayList<Song> shuffledList;
    private MusicService musicSrv;
    private Intent playIntent;
    private SharedPreferences prefs;
    private SharedPreferences.Editor prefsEditor;
    private boolean musicBound=false;
    private boolean isShuffled = false;

    private Song lastChosenPlaylist;
    public int lastPlaylistIndex;
    public Song lastChosenSong;

    private RelativeLayout audioController;
    private ImageView currSongArt;
    private TextView currSongInfo;
    private SeekBar seekBar;
    private TextView currTime;
    private ImageButton rewindBtn;
    private ImageButton playBtn;
    private ImageButton shuffBtn;
    private ImageButton ffBtn;
    private Handler handler;
    private float deviceHeight;
    private float deviceWidth;
    private boolean isFullscreen = false;
    GestureDetector gestureDetector;

    private boolean hasUpdated=false;
    private boolean paused=false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.activity_list_music);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefsEditor = prefs.edit();
        ActivityCompat.requestPermissions(ListMusic.this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},1);

        shuffledList = new ArrayList<>();
        songList = new ArrayList<>();
        artistList = new ArrayList<>();
        albumList = new ArrayList<>();
        folderList = new ArrayList<>();
        currSongArt = findViewById(R.id.currSongArt);
        currSongInfo = findViewById(R.id.currSongInfo);
        seekBar = findViewById(R.id.seekBar);
        rewindBtn = findViewById(R.id.rewindBtn);
        playBtn = findViewById(R.id.playBtn);
        ffBtn = findViewById(R.id.ffBtn);
        shuffBtn = findViewById(R.id.shuffButton);
        currTime = findViewById(R.id.currTime);
        handler = new Handler();
        audioController = findViewById(R.id.audioController);

        Gson gson = new Gson();
        String json = gson.toJson(shuffledList);

        final GestureDetector gdt = new GestureDetector(new GestureListener());

        Type type = new TypeToken<ArrayList<Song>>() {
        }.getType();
        playList = gson.fromJson(json, type);
        String response = prefs.getString("playList", "");
        playList = gson.fromJson(response, new TypeToken<ArrayList<Song>>() {
        }.getType());
        if (playList == null) {
            playList = new ArrayList<>();
        }

        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!musicSrv.isPng() && musicSrv.requestAudioFocus()) {
                    musicSrv.go();
                    playBtn.setImageResource(R.drawable.exo_controls_pause);
                } else {
                    musicSrv.pausePlayer();
                    playBtn.setImageResource(R.drawable.exo_controls_play);

                }
            }
        });

        rewindBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lastChosenSong = musicSrv.getSong();
                musicSrv.playPrev();
                playBtn.setImageResource(R.drawable.exo_controls_pause);
                updateSong();
            }
        });

        ffBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lastChosenSong = musicSrv.getSong();
                musicSrv.playNext();
                playBtn.setImageResource(R.drawable.exo_controls_pause);
                updateSong();
            }
        });

        audioController.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(final View view, final MotionEvent event) {
                gdt.onTouchEvent(event);
                return true;
            }
        });

        shuffBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!isShuffled) {
                    isShuffled = true;
                    Gson gson = new Gson();
                    String json = gson.toJson(shuffledList);
                    Type type = new TypeToken<ArrayList<Song>>() {
                    }.getType();
                    shuffledList = gson.fromJson(json, type);
                    String response = prefs.getString("shuffledList", "");
                    shuffledList = gson.fromJson(response, new TypeToken<ArrayList<Song>>() {
                    }.getType());

                    int savedIndex = prefs.getInt("shuffleIndex", 0);
                    if (shuffledList == null || shuffledList.size() == 0) {
                        Toast.makeText(ListMusic.this, "New shuffle created from current queue", Toast.LENGTH_LONG).show();
                        shuffledList = new ArrayList<>(musicSrv.getList());
                        Collections.shuffle(shuffledList);

                        for (int i = 0; i < shuffledList.size(); i++) {
                            if (shuffledList.get(i) == getCurrSong()) {
                                shuffledList.add(0, getCurrSong());
                                shuffledList.remove(i);
                            }
                        }

                        json = gson.toJson(shuffledList);
                        prefsEditor.remove("shuffledList").apply();
                        prefsEditor.putString("shuffledList", json);
                        prefsEditor.commit();
                    } else {
                        Toast.makeText(ListMusic.this, "Previous shuffle loaded", Toast.LENGTH_LONG).show();
                        int i = 0;
                        while (i < savedIndex) {
                            shuffledList.remove(0);
                            i++;
                        }

                        shuffledList.add(0, musicSrv.getSong());
                        json = gson.toJson(shuffledList);
                        prefsEditor.remove("shuffledList").apply();
                        prefsEditor.putString("shuffledList", json);
                        prefsEditor.commit();
                    }

                    musicSrv.setList(shuffledList);
                    musicSrv.setSong(0);
                    refreshQueue();
                }
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(musicSrv != null && fromUser) {
                    musicSrv.seek(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

    }


    @Override
    protected void onStart() {
        super.onStart();
        if(playIntent==null){
            DisplayMetrics displayMetrics = getApplicationContext().getResources().getDisplayMetrics();
            deviceHeight = displayMetrics.heightPixels;
            deviceWidth = displayMetrics.widthPixels;
            playIntent = new Intent(this, MusicService.class);
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(playIntent);
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                Bundle result = data.getBundleExtra("extra");
                lastChosenPlaylist = (Song)result.getSerializable("playlist");
                playList.set(lastPlaylistIndex, lastChosenPlaylist);
                ListView lv = findViewById(R.id.specPlaylist);
                SongAdapter songAdt = new SongAdapter(lv.getContext(), lastChosenPlaylist.getAlbumSongList(), "song");
                lv.setAdapter(songAdt);
                savePlaylistList();

            }
        }
    }



    // This method handles moving from one song to the next automatically.
    public void updateSong() {
        hasUpdated = true;
        flamenco.flamenco.Song currSong = musicSrv.getSong();
        currSongInfo.setText(currSong.getArtist()+" — "+currSong.getTitle());
        Glide.with(this).load(currSong.getAlbumArt()).error(R.drawable.placeholder)
                .crossFade().centerCrop().into(currSongArt);

        if (isShuffled) {
            prefsEditor.remove("shuffleIndex");
            prefsEditor.putInt("shuffleIndex", getCurrSongPosn());
            prefsEditor.commit();
        }

        int mediaPos = musicSrv.getPosn();
        int mediaMax = musicSrv.getDur();

        seekBar.setMax(mediaMax);
        seekBar.setProgress(mediaPos);

        updateCurrSong();

        handler.removeCallbacks(updateTime);
        handler.postDelayed(updateTime, 100);
    }


    public void refreshQueue(){
        QueueFragment queueFragment = (QueueFragment) getSupportFragmentManager().getFragments()
                .get(1).getChildFragmentManager().getFragments().get(1);
        queueFragment.refreshQueue();
    }

    public ArrayList<Song> getQueueList(){
        QueueFragment queueFragment = (QueueFragment) getSupportFragmentManager().getFragments()
                .get(1).getChildFragmentManager().getFragments().get(1);
        return queueFragment.getList();
    }


    // This method handles moving from one song to another based on user input
    public void songPicked(View view){
        lastChosenSong = musicSrv.getSong();
        Integer pos;
        // Check which list choice is coming from
        if (((ViewGroup)view.getParent()).getId() == R.id.a_song_list) {
            if (((ViewGroup)view.getParent().getParent().getParent()).getId() == R.id.artistView) {
                String albumTitle = (String) ((TextView)((ViewGroup)view.getParent().getParent())
                        .findViewById(R.id.albumFocusTitle)).getText();
                for (flamenco.flamenco.Song dog : albumList) {
                    if (dog.getTitle().equals(albumTitle)) {
                        musicSrv.setList(dog.getAlbumSongList());
                        break;
                    }
                }
            } else {
                musicSrv.setList(albumList.get(Integer.parseInt(((ListView) view.getParent()).getTag().toString()))
                        .getAlbumSongList());
            }
        } else if (((ViewGroup)view.getParent()).getId() == R.id.song_list) {
            musicSrv.setList(songList);
        } else if (((ViewGroup)view.getParent()).getId() == R.id.f_folder_list) {
            musicSrv.setList(lastFolder.getSongList());
        } else if (((ViewGroup)view.getParent()).getId() == R.id.specPlaylist) {
            musicSrv.setList(playList.get(lastPlaylistIndex).getAlbumSongList());
        }


        if (getCurrSongPosn() == 0) {
            ObjectAnimator animation = ObjectAnimator.ofFloat(audioController,
                "translationY", deviceHeight, 0).setDuration(500);
            animation.setInterpolator(new DecelerateInterpolator(2));
            animation.start();
        }


        pos = Integer.parseInt(view.getTag().toString());

        if (((ViewGroup)view.getParent()).getId() == R.id.queueList) {
            ArrayList<Song> queueList = getQueueList();
            int listDiff = musicSrv.getList().size() - queueList.size();
            pos = pos + listDiff;
        }

        musicSrv.setSong(pos);
        musicSrv.playSong();
        updateSong();

        if (((ViewGroup)view.getParent()).getId() != R.id.queueList) {
            isShuffled = false;
            refreshQueue();
        }
        //hasUpdated = true;

    }

    public void clearShuffle(View view) {
        Toast.makeText(ListMusic.this, "Shuffle deleted", Toast.LENGTH_LONG).show();
        shuffledList.clear();
        musicSrv.setList(songList);
        isShuffled = false;

        Gson gson = new Gson();
        String json = gson.toJson(shuffledList);

        prefsEditor.putInt("shuffleIndex", 0);
        prefsEditor.remove("shuffledList").apply();
        prefsEditor.putString("shuffledList", json);
        prefsEditor.commit();

        refreshQueue();
    }


    public void savePlaylistList() {
        Gson gson = new Gson();
        String json = gson.toJson(playList);

        prefsEditor.remove("playList").apply();
        prefsEditor.putString("playList", json);
        prefsEditor.commit();
    }

    public void updateCurrSong() {
        List<Fragment> allFragments = getSupportFragmentManager().getFragments();
        for (Fragment fragment: allFragments) {
            for (final Fragment child: fragment.getChildFragmentManager().getFragments()) {
                if (child instanceof SongsFragment) {
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            ((SongsFragment) child).updateCurrentSong();
                        }
                    }, 350);
                }
                if (child instanceof  QueueFragment) {
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            ((QueueFragment) child).updateCurrentSong();
                        }
                    }, 350);                }
            }
        }
    }


    // This method shows the Album Focus page when selecting an album
    public void setAlbumVisibility(View view) {
        RelativeLayout albumParent;
        Boolean artistsTab = false;
        TextView title = view.findViewById(R.id.album_title);
        ArrayList<flamenco.flamenco.Song> tempList;
        String albumTitle = (String)title.getText();

        if (view.getParent().getParent() == view.getRootView().findViewById(R.id.album_list_container)) {
            albumParent = (RelativeLayout) view.getParent().getParent().getParent();
            artistsTab = true;
        } else {
            albumParent = (RelativeLayout) view.getParent().getParent();
        }

        ListView tempAlbumList = albumParent.findViewById(R.id.a_song_list);

        tempList = albumList.get(0).getAlbumSongList();
        for (flamenco.flamenco.Song dog : albumList) {
            if (dog.getTitle().equals(albumTitle)) {
                tempList = dog.getAlbumSongList();
                break;
            }
        }

        ((TextView)albumParent.findViewById(R.id.albumFocusTitle)).setText(albumTitle);
        ((TextView)albumParent.findViewById(R.id.albumFocusArtist)).setText(tempList.get(0).getArtist());
        Glide.with(albumParent.getContext()).load(tempList.get(0).getAlbumArt())
                .error(R.drawable.placeholder).crossFade().centerCrop()
                .into((ImageView) albumParent.findViewById(R.id.albumFocusImage));

        ObjectAnimator animation;
        if (artistsTab) {
            animation = ObjectAnimator.ofFloat(albumParent.findViewById(R.id.album_list_container),
                    "translationY", 0, deviceHeight);
        } else {
            animation = ObjectAnimator.ofFloat(albumParent.findViewById(R.id.album_list),
                    "translationY", 0, deviceHeight);
        }
        animation.setDuration(300);
        animation.setStartDelay(225);
        animation.setInterpolator(new AccelerateInterpolator(2));
        animation.start();
        albumParent.findViewById(R.id.album_list).setAlpha(0.99f);

        tempAlbumList.setTag(view.getTag());

        animation = ObjectAnimator.ofFloat(albumParent.findViewById(R.id.albumFocus),
                "translationY", -70, 0).setDuration(225);
        animation.setDuration(300);
        animation.setStartDelay(225);
        animation.start();

        albumParent.findViewById(R.id.albumFocus).setAlpha(1f);


        SongAdapter songAdt = new SongAdapter(albumParent.getContext(), tempList, "album");
        tempAlbumList.setAdapter(songAdt);

    }

    public void setPlaylistVisibility(final View view) {
        TextView title = view.findViewById(R.id.playlist_title);
        ArrayList<flamenco.flamenco.Song> tempList;
        String playlistTitle = (String)title.getText();
        RelativeLayout playlistParent = (RelativeLayout) view.getParent().getParent().getParent().getParent();
        ListView tempPlayList = playlistParent.findViewById(R.id.specPlaylist);

        tempList = playList.get(0).getAlbumSongList();
        for (flamenco.flamenco.Song dog : playList) {
            if (dog.getTitle().equals(playlistTitle)) {
                lastPlaylistIndex = playList.indexOf(dog);
                lastChosenPlaylist = dog;
                tempList = dog.getAlbumSongList();
                break;
            }
        }

        ObjectAnimator animation = ObjectAnimator.ofFloat(playlistParent.findViewById(R.id.playlist_init),
                "translationY", 0, deviceHeight);
        animation.setDuration(300);
        animation.setStartDelay(225);
        animation.setInterpolator(new AccelerateInterpolator(2));
        animation.start();

        animation = ObjectAnimator.ofFloat(playlistParent.findViewById(R.id.playlistFocus),
                "translationY", -70, 0);
        animation.setDuration(225);
        animation.setStartDelay(225);
        animation.start();

        ((FloatingActionButton)view.getRootView().findViewById(R.id.addNew)).hide();
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ((FloatingActionButton)view.getRootView().findViewById(R.id.floatingActionButton2)).show();
            }
        }, 550);

        SongAdapter songAdt = new SongAdapter(playlistParent.getContext(), tempList, "song");
        tempPlayList.setAdapter(songAdt);

    }

    public void setFolderVisibility(View view) {
        ArrayList<Folder> tempFolderList = new ArrayList<>();
        ArrayList<Folder> tempSearchFolderList;
        ArrayList<Song> tempSongList = new ArrayList<>();
        String path = (String)((TextView)view.findViewById(R.id.folder_path)).getText();
        View parentView = view.getRootView();
        ListView tempFolderView = parentView.findViewById(R.id.f_folder_list);

        if (lastFolder == null) {
            tempSearchFolderList = folderList;

            ObjectAnimator animation = ObjectAnimator.ofFloat(parentView.findViewById(R.id.folder_list),
                    "translationY", 0, deviceHeight);
            animation.setDuration(300);
            animation.setStartDelay(225);
            animation.setInterpolator(new AccelerateInterpolator(2));
            animation.start();

            animation = ObjectAnimator.ofFloat(parentView.findViewById(R.id.folderFocus),
                    "translationY", -70, 0);
            animation.setDuration(225);
            animation.setStartDelay(225);
            animation.start();


        } else {
            tempSearchFolderList = lastFolder.getFolderList();

            ObjectAnimator animation = ObjectAnimator.ofFloat(parentView.findViewById(R.id.folderFocus),
                    "translationY", -deviceHeight, 0);
            animation.setDuration(225);
            animation.setStartDelay(225);
            animation.setInterpolator(new AccelerateInterpolator(3));
            animation.start();

        }

        for (Folder folder : tempSearchFolderList) {
            if (folder.getPath().equals(path)) {
                tempFolderList = folder.getFolderList();
                tempSongList = folder.getSongList();
                lastFolder = folder;
                break;
            }
        }

        FoldersAdapter foldersAdapter = new FoldersAdapter(view.getContext(), tempFolderList, tempSongList);
        tempFolderView.setAdapter(foldersAdapter);

    }

    public void addSongToPlaylist(View view) {
        Intent intent = new Intent(this, AddSongToPlaylist.class);

        Bundle extra = new Bundle();
        extra.putSerializable("songs", songList);
        intent.putExtra("extra", extra);

        intent.putExtra("extra2", lastChosenPlaylist);
        startActivityForResult(intent,1);
    }

    // This method shows the albums belonging to an artist
    public void showAlbums(View view) {
        ArrayList<flamenco.flamenco.Song> tempAlbumList = new ArrayList<>();
        TextView title = view.findViewById(R.id.album_artist);
        String artistName = (String) title.getText();
        final View parentView = (View) view.getParent().getParent();

        for (flamenco.flamenco.Song dog : albumList) {
            if (dog.getArtist().equals(artistName)) {
                tempAlbumList.add(dog);
            }
        }

        ObjectAnimator animation = ObjectAnimator.ofFloat(parentView.findViewById(R.id.artist_list),
                "translationY", 0, deviceHeight);
        animation.setDuration(300);
        animation.setInterpolator(new AccelerateInterpolator(2));
        animation.start();
        parentView.findViewById(R.id.artist_list).setAlpha(0.99f);

        animation = ObjectAnimator.ofFloat(parentView.findViewById(R.id.album_list_container),
                "translationY", -70, 0);
        animation.setDuration(225);
        animation.start();
        parentView.findViewById(R.id.album_list).setAlpha(1f);

        GridView albumView = parentView.findViewById(R.id.album_list);
        AlbumAdapter songAdt = new AlbumAdapter(view.getContext(), tempAlbumList, "albums");
        albumView.setAdapter(songAdt);
    }


    private ServiceConnection musicConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicBinder binder = (MusicBinder)service;
            musicSrv = binder.getService();
            musicSrv.setList(songList);
            musicBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
        }
    };


    // This method handles the storage permissions
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission granted and now can proceed
                    getSongList();
                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(ListMusic.this, "Permission denied to read your External storage",
                            Toast.LENGTH_SHORT).show();
                }
            }
            // add other cases for more permissions
        }
    }


    @Override
    public void start() {
        musicSrv.go();
    }

    @Override
    public void pause() {
        musicSrv.pausePlayer();
    }

    @Override
    public int getDuration() {
        if(musicSrv!=null && musicBound && musicSrv.isPng())
            return musicSrv.getDur();
        else return 0;
    }

    @Override
    public int getCurrentPosition() {
        if(musicSrv!=null && musicBound && musicSrv.isPng())
        return musicSrv.getPosn();
        else return 0;
    }

    @Override
    public void seekTo(int pos) {
        musicSrv.seek(pos);
    }

    @Override
    public boolean isPlaying() {
        if(musicSrv!=null && musicBound)
        return musicSrv.isPng();
        return false;
    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return false;
    }

    @Override
    public boolean canSeekForward() {
        return false;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    @Override
    protected void onPause(){
        super.onPause();
        paused=true;
    }

    @Override
    protected void onResume(){
        super.onResume();
        if(paused){
            //setController();
            paused=false;
        }
    }

    @Override
    protected void onStop() {
        //controller.hide();
        super.onStop();
    }


    // This method runs continually and updates the seekbar
    private Runnable updateTime = new Runnable() {
        @Override
        public void run() {
            int songPos = musicSrv.getPosn();
            int songDur = musicSrv.getDur();
            seekBar.setMax(songDur);
            seekBar.setProgress(songPos);
            currTime.setText("" + milliSecondsToTimer(songPos));

            handler.postDelayed(this, 100);
        }
    };


    // This method assists the above, converting song position to human readable form
    public String milliSecondsToTimer(long milliseconds){
        String finalTimerString = "";
        String secondsString;

        int hours = (int)( milliseconds / (1000*60*60));
        int minutes = (int)(milliseconds % (1000*60*60)) / (1000*60);
        int seconds = (int) ((milliseconds % (1000*60*60)) % (1000*60) / 1000);
        if(hours > 0){
            finalTimerString = hours + ":";
        }

        if (seconds == 0) {
            if (!hasUpdated) {
                hasUpdated = true;
                lastChosenSong = musicSrv.getSong();
                updateSong();
            }
            if (musicSrv.isPng()) {
                playBtn.setImageResource(R.drawable.exo_controls_pause);
            }
        }

        if (seconds > 0) {
            hasUpdated = false;
        }

        if(seconds < 10){
            secondsString = "0" + seconds;
        } else {
            secondsString = "" + seconds;}

        finalTimerString = finalTimerString + minutes + ":" + secondsString;
        return finalTimerString;
    }


    // This is a helper method for fragments that need to access the currently playing song list
    public ArrayList<Song> getServiceList() {
        return musicSrv.getList();
    }

    public Integer getCurrSongPosn() {
        try {
            return musicSrv.getSongPosn();
        } catch (NullPointerException e) {
            return 0;
        }
    }

    public Song getCurrSong() {
        try {
            return musicSrv.getSong();
        } catch (NullPointerException e) {
            return null;
        }
    }

    public boolean addFolders(Folder folder, Song song, String path) {
        String[] splitPath = path.split("/");
        StringBuilder buildPath = new StringBuilder("/"+splitPath[0]);
        for (int i = 1; i < splitPath.length; i++) {
            buildPath.append(splitPath[i]).append('/');
            if (buildPath.toString().equals(folder.getPath())) {
                if (i == splitPath.length-1) {
                    folder.addToSongList(song);
                    return true;
                } else {
                    buildPath.append(splitPath[i+1]).append('/');
                    for (Folder subFolder : folder.getFolderList()) {
                        if (subFolder.getPath().equals(buildPath.toString())) {
                            return addFolders(subFolder, song, path);
                        }
                    }
                    Folder newFolder = new Folder(path, new ArrayList<Song>(), new ArrayList<Folder>(), folder);
                    newFolder.addToSongList(song);
                    folder.addToFolderList(newFolder);
                    return true;
                }
            }
        }
        return false;

    }

    // This method runs on startup, constructing a list of songs, artists, and albums
    public void getSongList() {
        ArrayList<flamenco.flamenco.Song> albumSongList = new ArrayList<>();
        ContentResolver musicResolver = getContentResolver();
        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);
        if (musicCursor != null && musicCursor.moveToFirst()) {
            int titleColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Albums.ALBUM);
            int artistColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Albums.ARTIST);
            int yearColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.YEAR);
            int isMusic = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.IS_MUSIC);
            int songTitleColumn = musicCursor.getColumnIndex
                    (android.provider.MediaStore.Audio.Media.TITLE);
            int idColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media._ID);
            int albumIdColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.ALBUM_ID);
            int pathColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.DATA);
            do {

                String thisYear = musicCursor.getString(yearColumn);
                int thisIsMusic = musicCursor.getInt(isMusic);
                String thisArtist = musicCursor.getString(artistColumn);
                String thisTitle = musicCursor.getString(titleColumn);
                String thisSongTitle = musicCursor.getString(songTitleColumn);
                String thisPath = musicCursor.getString(pathColumn);
                long thisId = musicCursor.getLong(idColumn);
                long thisAlbumId = musicCursor.getLong(albumIdColumn);
                Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
                Uri albumArt = ContentUris.withAppendedId(sArtworkUri, thisAlbumId);

                if (thisIsMusic > 0) {


                    String path = thisPath.substring(0, thisPath.lastIndexOf("/"));


                    boolean hasBeenAdded = false;
                    for (int i=0; i<folderList.size(); i++) {
                        if (addFolders(folderList.get(i), new Song(thisId, thisSongTitle, thisArtist, thisAlbumId, thisYear, albumArt.toString()), path+"/")) {
                            hasBeenAdded = true;
                            break;
                        }
                    }

                    if (!hasBeenAdded) {
                        folderList.add(new Folder(path+"/", new ArrayList<Song>(), new ArrayList<Folder>(), null));
                        folderList.get(folderList.size()-1).addToSongList(new Song(thisId, thisSongTitle, thisArtist, thisAlbumId, thisYear, albumArt.toString()));
                    }


                    if (albumList.size() == 0) {
                        albumList.add(new flamenco.flamenco.Song(thisId, thisTitle, thisArtist, thisAlbumId, thisYear, albumArt.toString()));
                        artistList.add(new flamenco.flamenco.Song(thisId, thisSongTitle, thisArtist, thisAlbumId, thisYear, albumArt.toString()));
                    } else if (!albumList.get(albumList.size()-1).getTitle().equals(thisTitle)) {
                        albumList.get(albumList.size() -1).setAlbumSongList(albumSongList);

                        Song tempArtist = new Song(thisId, thisArtist, thisArtist, thisAlbumId, thisYear, albumArt.toString());
                        boolean artistFound = false;
                        for (int i=0;i<artistList.size();i++) {
                            if (artistList.get(i).getArtist().equals(tempArtist.getArtist())) {
                                artistFound = true;
                            }
                        }
                        if (!artistFound) {
                            artistList.add(tempArtist);
                        }

                        albumList.add(new flamenco.flamenco.Song(thisId, thisTitle, thisArtist, thisAlbumId, thisYear, albumArt.toString()));
                        albumSongList.clear();
                    }
                    albumSongList.add(new flamenco.flamenco.Song(thisId, thisSongTitle, thisArtist, thisAlbumId, thisYear, albumArt.toString()));
                    songList.add(new flamenco.flamenco.Song(thisId, thisSongTitle, thisArtist, thisAlbumId, thisYear, albumArt.toString()));
                }
            }
            while (musicCursor.moveToNext());
        }

        try {
            albumList.get(albumList.size() - 1).setAlbumSongList(albumSongList);
        } catch (ArrayIndexOutOfBoundsException e) {
            Toast.makeText(ListMusic.this, "No audio found!", Toast.LENGTH_SHORT).show();
        }
        musicCursor.close();

        Collections.sort(songList, new Comparator<flamenco.flamenco.Song>() {
            @Override
            public int compare(flamenco.flamenco.Song o1, flamenco.flamenco.Song o2) {
                return o1.getTitle().compareTo(o2.getTitle());
            }
        });
        Collections.sort(albumList, new Comparator<flamenco.flamenco.Song>() {
            @Override
            public int compare(flamenco.flamenco.Song o1, flamenco.flamenco.Song o2) {
                return o1.getTitle().compareTo(o2.getTitle());
            }
        });
        Collections.sort(artistList, new Comparator<flamenco.flamenco.Song>() {
            @Override
            public int compare(flamenco.flamenco.Song o1, flamenco.flamenco.Song o2) {
                return o1.getArtist().compareTo(o2.getArtist());
            }
        });

        // Initiate UI after cataloguing songs
        final ViewPager viewPager = findViewById(R.id.pager);
        final MainFragmentAdapter adapter = new MainFragmentAdapter(
                getSupportFragmentManager(), 2);
        viewPager.setAdapter(adapter);
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

            if(e1.getY() - e2.getY() > 10) {
                RelativeLayout songController = audioController.findViewById(R.id.songController);

                RelativeLayout.LayoutParams parms = new RelativeLayout.LayoutParams(audioController.getWidth(), (int) deviceHeight);
                audioController.getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);
                audioController.setLayoutParams(parms);
                audioController.requestLayout();

                audioController.findViewById(R.id.currSongArt).animate().translationX(deviceWidth/2.75f)
                        .setDuration(500).start();
                audioController.findViewById(R.id.currSongArt).animate().translationY(deviceHeight/5)
                        .setDuration(500).start();
                audioController.findViewById(R.id.currSongArt).animate().scaleX(3.5f)
                        .setDuration(500).start();
                audioController.findViewById(R.id.currSongArt).animate().scaleY(3.5f)
                        .setDuration(500).start();

                audioController.findViewById(R.id.songController).animate().translationX(-deviceWidth/6)
                        .setInterpolator(new DecelerateInterpolator(2)).setDuration(450).start();
                audioController.findViewById(R.id.songController).animate().translationY(deviceHeight/2.25f)
                        .setInterpolator(new DecelerateInterpolator(2)).setDuration(450).start();

                return false; // Bottom to top
            }  else if (e2.getY() - e1.getY() > 10) {
                RelativeLayout.LayoutParams parms = new RelativeLayout.LayoutParams(audioController.getWidth(), (int)(100 * getResources().getDisplayMetrics().density));
                parms.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                audioController.getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);
                audioController.setLayoutParams(parms);
                audioController.requestLayout();

                audioController.findViewById(R.id.currSongArt).animate().translationX(0)
                        .setInterpolator(new DecelerateInterpolator(2)).setDuration(500).start();
                audioController.findViewById(R.id.currSongArt).animate().translationY(0)
                        .setInterpolator(new DecelerateInterpolator(2)).setDuration(500).start();
                audioController.findViewById(R.id.currSongArt).animate().scaleX(1)
                        .setInterpolator(new DecelerateInterpolator(2)).setDuration(500).start();
                audioController.findViewById(R.id.currSongArt).animate().scaleY(1)
                        .setInterpolator(new DecelerateInterpolator(2)).setDuration(500).start();

                audioController.findViewById(R.id.songController).animate().translationX(0)
                        .setInterpolator(new DecelerateInterpolator(2)).setDuration(450).start();
                audioController.findViewById(R.id.songController).animate().translationY(0)
                        .setInterpolator(new DecelerateInterpolator(2)).setDuration(450).start();

                return false; // Top to bottom
            }
            return false;
        }
    }

}
