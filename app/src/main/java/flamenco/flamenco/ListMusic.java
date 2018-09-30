package flamenco.flamenco;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.os.IBinder;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.view.View;
import flamenco.flamenco.MusicService.MusicBinder;
import android.widget.MediaController.MediaPlayerControl;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class ListMusic extends AppCompatActivity implements MediaPlayerControl {

    private ArrayList<Song> songList;
    private ListView songView;
    private MusicService musicSrv;
    private Intent playIntent;
    private boolean musicBound=false;

    private ImageView currSongArt;
    private TextView currSongInfo;
    private SeekBar seekBar;
    private Button rewindBtn;
    private Button playBtn;
    private Button ffBtn;
    private Handler handler;


    private boolean paused=false, playbackPaused=false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_music);
        songView = (ListView)findViewById(R.id.song_list);
        songList = new ArrayList<Song>();
        currSongArt = findViewById(R.id.currSongArt);
        currSongInfo = findViewById(R.id.currSongInfo);
        seekBar = findViewById(R.id.seekBar);
        rewindBtn = (Button) findViewById(R.id.rewindBtn);
        playBtn = findViewById(R.id.playBtn);
        ffBtn = findViewById(R.id.ffBtn);
        handler = new Handler();

        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (playbackPaused) {
                    musicSrv.go();
                    playbackPaused = false;
                } else {
                    musicSrv.pausePlayer();
                    playbackPaused = true;
                }
                updateSong();
            }
        });

        rewindBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                musicSrv.playPrev();
                updateSong();
            }
        });

        ffBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                musicSrv.playNext();
                updateSong();
            }
        });

        ActivityCompat.requestPermissions(ListMusic.this,
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},1);

    }


    @Override
    protected void onStart() {
        super.onStart();


        if(playIntent==null){
            playIntent = new Intent(this, MusicService.class);
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(playIntent);
        }
    }


    public void updateSong() {
        Song currSong = musicSrv.getSong();
        currSongInfo.setText(currSong.getArtist()+" — "+currSong.getTitle());
        Glide.with(this).load(currSong.getAlbumArt()).error(R.drawable.placeholder)
                .crossFade().dontAnimate().centerCrop().into(currSongArt);

        int mediaPos = musicSrv.getPosn();
        int mediaMax = musicSrv.getDur();

        seekBar.setMax(mediaMax);
        seekBar.setProgress(mediaPos);

        handler.removeCallbacks(updateTime);
        handler.postDelayed(updateTime, 100);
    }


    public void songPicked(View view){

        musicSrv.setSong(Integer.parseInt(view.getTag().toString()));
        musicSrv.playSong();
        updateSong();

        if(playbackPaused){
            //setController();
            playbackPaused=false;
        }
        //controller.show(0);
    }


    private ServiceConnection musicConnection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicBinder binder = (MusicBinder)service;
            //get service
            musicSrv = binder.getService();
            //pass list
            musicSrv.setList(songList);
            musicBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
        }
    };


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
                    Toast.makeText(ListMusic.this, "Permission denied to read your External storage", Toast.LENGTH_SHORT).show();
                }
            }
            // add other cases for more permissions
        }
    }


    public void getSongList() {
        ContentResolver musicResolver = getContentResolver();
        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);
        if (musicCursor != null && musicCursor.moveToFirst()) {
            int titleColumn = musicCursor.getColumnIndex
                    (android.provider.MediaStore.Audio.Media.TITLE);
            int idColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media._ID);
            int artistColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.ARTIST);
            int yearColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.YEAR);
            int albumIdColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.ALBUM_ID);
            do {
                String thisTitle = musicCursor.getString(titleColumn);
                String thisArtist = musicCursor.getString(artistColumn);
                String thisYear = musicCursor.getString(yearColumn);
                long thisId = musicCursor.getLong(idColumn);
                long thisAlbumId = musicCursor.getLong(albumIdColumn);
                songList.add(new Song(thisId, thisTitle, thisArtist, thisAlbumId, thisYear));
            }
            while (musicCursor.moveToNext());
        }
        musicCursor.close();

        Collections.sort(songList, new Comparator<Song>() {
            @Override
            public int compare(Song o1, Song o2) {
                return o1.getTitle().compareTo(o2.getTitle());
            }
        });

        SongAdapter songAdt = new SongAdapter(this, songList);
        songView.setAdapter(songAdt);
    }


    private void playNext(){
        musicSrv.playNext();
        if(playbackPaused){
            //setController();
            playbackPaused=false;
        }
        //controller.show(0);
    }


    //play previous
    private void playPrev(){
        musicSrv.playPrev();
        if(playbackPaused){
            //setController();
            playbackPaused=false;
        }
       //controller.show(0);
    }


    private BroadcastReceiver onPrepareReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            // When music player has been prepared, show controller
            //controller.show(0);
        }
    };


    @Override
    public void start() {
        musicSrv.go();
    }

    @Override
    public void pause() {
        playbackPaused=true;
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
        LocalBroadcastManager.getInstance(this).registerReceiver(onPrepareReceiver,
                new IntentFilter("MEDIA_PLAYER_PREPARED"));
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


    private Runnable updateTime = new Runnable() {
        @Override
        public void run() {
            if (!playbackPaused) {
                int songPos = musicSrv.getPosn();
                int songDur = musicSrv.getDur();
                seekBar.setMax(songDur);
                seekBar.setProgress(songPos);

                handler.postDelayed(this, 100);
            }
        }
    };
}
