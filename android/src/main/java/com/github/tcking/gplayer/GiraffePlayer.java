package com.github.tcking.gplayer;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.view.Surface;
import android.view.ViewGroup;
import android.widget.MediaController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.PluginRegistry;
import io.flutter.view.TextureRegistry;
import tv.danmaku.ijk.media.player.AndroidMediaPlayer;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.IjkTimedText;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;


/**
 * Created by tcking on 2017
 */

public class GiraffePlayer implements MediaController.MediaPlayerControl {
    public static final String TAG = "GiraffePlayer";
    public static boolean debug = true;
    public static boolean nativeDebug = false;
    // Internal messages
    private static final int MSG_CTRL_PLAYING = 1;

    private static final int MSG_CTRL_PAUSE = 2;
    private static final int MSG_CTRL_SEEK = 3;
    private static final int MSG_CTRL_RELEASE = 4;
    private static final int MSG_CTRL_RETRY = 5;
    private static final int MSG_CTRL_SELECT_TRACK = 6;
    private static final int MSG_CTRL_DESELECT_TRACK = 7;
    private static final int MSG_CTRL_SET_VOLUME = 8;


    private static final int MSG_SET_DISPLAY = 12;


    // all possible internal states
    public static final int STATE_ERROR = -1;
    public static final int STATE_IDLE = 0;
    public static final int STATE_PREPARING = 1;
    public static final int STATE_PREPARED = 2;
    public static final int STATE_PLAYING = 3;
    public static final int STATE_PAUSED = 4;
    public static final int STATE_PLAYBACK_COMPLETED = 5;
    public static final int STATE_RELEASE = 6;
    public static final int STATE_LAZYLOADING = 7;
    public static final int STATE_BUFFERING = 8;

    private final HandlerThread internalPlaybackThread;
    private TextureRegistry.SurfaceTextureEntry surfaceTextureEntry;

    private int currentBufferPercentage = 0;
    private boolean canPause = true;
    private boolean canSeekBackward = true;
    private boolean canSeekForward = true;
    private int audioSessionId;

    private int currentState = STATE_IDLE;
    private int targetState = STATE_IDLE;
    private Uri uri;
    private Map<String, String> headers = new HashMap<>();

    private IMediaPlayer mediaPlayer;
    private volatile boolean released;
    private Handler handler;
    private PlayerListener playerListener;

    private volatile int startPosition = -1;
    private boolean mute = false;

    private VideoInfo videoInfo;
    private Context context;
    private Surface surface;


    private PlayerListener proxyListener() {
        return playerListener;
    }


    protected GiraffePlayer(final VideoInfo videoInfo) {
        final PluginRegistry.Registrar registrar = PlayerManager.getInstance().getRegistrar();
        this.videoInfo = videoInfo;
        this.playerListener = new FlutterPlayerListener(videoInfo.getFingerprint());

        this.context = registrar.context();
        log("new GiraffePlayer");
        internalPlaybackThread = new HandlerThread("GiraffePlayerInternal:Handler", Process.THREAD_PRIORITY_AUDIO);
        internalPlaybackThread.start();
        handler = new Handler(internalPlaybackThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                //init mediaPlayer before any actions
                log("handleMessage:" + msg.what);
                if (msg.what == MSG_CTRL_RELEASE) {
                    if (!released) {
                        handler.removeCallbacks(null);
                        currentState(STATE_RELEASE);
                        doRelease(((String) msg.obj));
                    }
                    return true;
                }
                if (mediaPlayer == null || released) {
                    handler.removeCallbacks(null);
                    try {
                        init();
                        handler.sendMessage(Message.obtain(msg));
                    } catch (UnsatisfiedLinkError e) {
                        log("UnsatisfiedLinkError:" + e);
                        currentState(STATE_LAZYLOADING);
                        LazyLoadManager.Load(context, videoInfo.getFingerprint(), Message.obtain(msg));
                    }
                    return true;
                }
                switch (msg.what) {
                    case MSG_CTRL_PLAYING:
                        if (currentState == STATE_ERROR) {
                            handler.sendEmptyMessage(MSG_CTRL_RETRY);
                        } else if (isInPlaybackState()) {
                            if (canSeekForward) {
                                if (currentState == STATE_PLAYBACK_COMPLETED) {
                                    startPosition = 0;
                                }
                                if (startPosition >= 0) {
                                    mediaPlayer.seekTo(startPosition);
                                    startPosition = -1;
                                }
                            }
                            mediaPlayer.start();
                            currentState(STATE_PLAYING);
                        }
                        break;
                    case MSG_CTRL_PAUSE:
                        mediaPlayer.pause();
                        currentState(STATE_PAUSED);
                        break;
                    case MSG_CTRL_SEEK:
                        if (!canSeekForward) {
                            break;
                        }
                        int position = (int) msg.obj;
                        mediaPlayer.seekTo(position);
                        break;
                    case MSG_CTRL_SELECT_TRACK:
                        int track = (int) msg.obj;
                        if (mediaPlayer instanceof IjkMediaPlayer) {
                            ((IjkMediaPlayer) mediaPlayer).selectTrack(track);
                        } else if (mediaPlayer instanceof AndroidMediaPlayer) {
                            ((AndroidMediaPlayer) mediaPlayer).getInternalMediaPlayer().selectTrack(track);
                        }
                        break;
                    case MSG_CTRL_DESELECT_TRACK:
                        int deselectTrack = (int) msg.obj;
                        if (mediaPlayer instanceof IjkMediaPlayer) {
                            ((IjkMediaPlayer) mediaPlayer).deselectTrack(deselectTrack);
                        } else if (mediaPlayer instanceof AndroidMediaPlayer) {
                            ((AndroidMediaPlayer) mediaPlayer).getInternalMediaPlayer().deselectTrack(deselectTrack);
                        }
                        break;
                    case MSG_SET_DISPLAY:
//                        if(surfaceTextureEntry!=null){
//                            surfaceTextureEntry.release();
//                        }
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {

                                if (surfaceTextureEntry == null) {
                                    surfaceTextureEntry = registrar.textures().createSurfaceTexture();
                                }
                                if (surface != null) {
                                    surface.release();
                                }
                                surface = new Surface(surfaceTextureEntry.surfaceTexture());
                                mediaPlayer.setSurface(surface);
                                proxyListener().onSetDisplay(GiraffePlayer.this, surfaceTextureEntry.id());
                            }
                        });
                        break;
                    case MSG_CTRL_RETRY:
                        init();
                        handler.sendEmptyMessage(MSG_CTRL_PLAYING);
                        break;
                    case MSG_CTRL_SET_VOLUME:
                        Map<String, Float> pram = (Map<String, Float>) msg.obj;
                        mediaPlayer.setVolume(pram.get("left"), pram.get("right"));
                        break;

                    default:
                }
                return true;
            }
        });
    }


    private boolean isInPlaybackState() {
        return (mediaPlayer != null &&
                currentState != STATE_ERROR &&
                currentState != STATE_IDLE &&
                currentState != STATE_PREPARING);
    }


    @Override
    public void start() {
        if (currentState == STATE_PLAYBACK_COMPLETED && !canSeekForward) {
            releaseMediaPlayer();
        }
        targetState(STATE_PLAYING);
        handler.sendEmptyMessage(MSG_CTRL_PLAYING);
        proxyListener().onStart(this);
    }

    private void targetState(final int newState) {
        final int oldTargetState = targetState;
        targetState = newState;
        if (oldTargetState != newState) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    proxyListener().onTargetStateChange(oldTargetState, newState);
                }
            });
        }
    }

    private void currentState(final int newState) {
        final int oldCurrentState = currentState;
        currentState = newState;
        if (oldCurrentState != newState) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    proxyListener().onCurrentStateChange(oldCurrentState, newState);

                }
            });
        }
    }

    @Override
    public void pause() {
        targetState(STATE_PAUSED);
        handler.sendEmptyMessage(MSG_CTRL_PAUSE);
//        playerListener().onPause(this);
    }

    @Override
    public int getDuration() {
        if (mediaPlayer == null) {
            return 0;
        }
        return (int) mediaPlayer.getDuration();
    }

    @Override
    public int getCurrentPosition() {
        if (mediaPlayer == null) {
            return 0;
        }
        return (int) mediaPlayer.getCurrentPosition();
    }

    @Override
    public void seekTo(int pos) {
        handler.obtainMessage(MSG_CTRL_SEEK, pos).sendToTarget();
    }

    @Override
    public boolean isPlaying() {
        //mediaPlayer.isPlaying()
        return currentState == STATE_PLAYING;
    }

    @Override
    public int getBufferPercentage() {
        return currentBufferPercentage;
    }

    @Override
    public boolean canPause() {
        return canPause;
    }

    @Override
    public boolean canSeekBackward() {
        return canSeekBackward;
    }

    @Override
    public boolean canSeekForward() {
        return canSeekForward;
    }

    @Override
    public int getAudioSessionId() {
        if (audioSessionId == 0) {
            audioSessionId = mediaPlayer.getAudioSessionId();
        }
        return audioSessionId;
    }


    /**
     * Sets video path.
     *
     * @param path the path of the video.
     */
    private GiraffePlayer setVideoPath(String path) throws IOException {
        return setVideoURI(Uri.parse(path));
    }

    /**
     * Sets video URI.
     *
     * @param uri the URI of the video.
     */
    private GiraffePlayer setVideoURI(Uri uri) throws IOException {
        return setVideoURI(uri, null);
    }

    /**
     * Sets video URI using specific headers.
     *
     * @param uri     the URI of the video.
     * @param headers the headers for the URI request.
     *                Note that the cross domain redirection is allowed by default, but that can be
     *                changed with key/value pairs through the headers parameter with
     *                "android-allow-cross-domain-redirect" as the key and "0" or "1" as the value
     *                to disallow or allow cross domain redirection.
     */
    private GiraffePlayer setVideoURI(Uri uri, Map<String, String> headers) throws IOException {
        this.uri = uri;
        this.headers.clear();
        this.headers.putAll(headers);
        return this;
    }

    private void init() {
        log("init");
        releaseMediaPlayer();
        mediaPlayer = createMediaPlayer();
        if (mediaPlayer instanceof IjkMediaPlayer) {
            IjkMediaPlayer.native_setLogLevel(nativeDebug ? IjkMediaPlayer.IJK_LOG_DEBUG : IjkMediaPlayer.IJK_LOG_ERROR);
        }
        setOptions();
        released = false;
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setLooping(videoInfo.isLooping());
        mediaPlayer.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(IMediaPlayer iMediaPlayer) {
                boolean live = mediaPlayer.getDuration() == 0;
                canSeekBackward = !live;
                canSeekForward = !live;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        proxyListener().onPrepared(GiraffePlayer.this);
                    }
                });
                currentState(STATE_PREPARED);
                if (targetState == STATE_PLAYING) {
                    handler.sendEmptyMessage(MSG_CTRL_PLAYING);
                }
            }
        });
        initInternalListener();
        handler.obtainMessage(MSG_SET_DISPLAY).sendToTarget();
        try {
            uri = videoInfo.getUri();
            mediaPlayer.setDataSource(context, uri, headers);
            currentState(STATE_PREPARING);
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    proxyListener().onError(GiraffePlayer.this, 0, 0, "network error");
                }
            });
            currentState(STATE_ERROR);
        }

    }

    private IMediaPlayer createMediaPlayer() {
        if (VideoInfo.PLAYER_IMPL_SYSTEM.equals(videoInfo.getPlayerImpl())) {
            return new AndroidMediaPlayer();
        }
        return new IjkMediaPlayer(Looper.getMainLooper());
    }

    private void setOptions() {
        headers.clear();
        if (videoInfo.getOptions().size() <= 0) {
            return;
        }
        //https://ffmpeg.org/ffmpeg-protocols.html#http
        if (mediaPlayer instanceof IjkMediaPlayer) {
            IjkMediaPlayer ijkMediaPlayer = (IjkMediaPlayer) mediaPlayer;
            for (Option option : videoInfo.getOptions()) {
                if (option.getValue() instanceof String) {
                    ijkMediaPlayer.setOption(option.getCategory(), option.getName(), ((String) option.getValue()));
                } else if (option.getValue() instanceof Integer) {
                    ijkMediaPlayer.setOption(option.getCategory(), option.getName(), Long.valueOf((Integer) option.getValue()));
                }
            }
        } else if (mediaPlayer instanceof AndroidMediaPlayer) {
            for (Option option : videoInfo.getOptions()) {
                if (IjkMediaPlayer.OPT_CATEGORY_FORMAT == option.getCategory() && "headers".equals(option.getName())) {
                    String h = "" + option.getValue();
                    String[] hs = h.split("\r\n");
                    for (String hd : hs) {
                        String[] kv = hd.split(":");
                        String v = kv.length >= 2 ? kv[1] : "";
                        headers.put(kv[0], v);
                        log("add header " + kv[0] + ":" + v);
                    }
                    break;
                }
            }
        }
    }

    private void initInternalListener() {
        //playerListener fire on main thread
        mediaPlayer.setOnBufferingUpdateListener(new IMediaPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(IMediaPlayer iMediaPlayer, int percent) {
                currentBufferPercentage = percent;
                proxyListener().onBufferingUpdate(GiraffePlayer.this, percent);
            }
        });
        mediaPlayer.setOnInfoListener(new IMediaPlayer.OnInfoListener() {
            private int lastCurrentState;

            //https://developer.android.com/reference/android/media/MediaPlayer.OnInfoListener.html
            @Override
            public boolean onInfo(IMediaPlayer iMediaPlayer, final int what, final int extra) {

                if (what == IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED) {
//                    ScalableTextureView currentDisplay = getCurrentDisplay();
//                    if (currentDisplay != null) {
//                        currentDisplay.setRotation(extra);
//                    }
                }
                log("onInfo:" + what);
                if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    lastCurrentState = currentState;
                    currentState(STATE_BUFFERING);
                } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    if (lastCurrentState == targetState) {
                        currentState(lastCurrentState);
                    }
                }


//                handler.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        playerListener().onInfo(GiraffePlayer.this, what, extra);
//                    }
//                });

                return true;
            }
        });
        mediaPlayer.setOnCompletionListener(new IMediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(IMediaPlayer iMediaPlayer) {
                currentState(STATE_PLAYBACK_COMPLETED);
                proxyListener().onCompletion(GiraffePlayer.this);
            }
        });
        mediaPlayer.setOnErrorListener(new IMediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(IMediaPlayer iMediaPlayer, int what, int extra) {
                log("setOnErrorListener");
                boolean b = proxyListener().onError(GiraffePlayer.this, what, extra, null);
                currentState(STATE_ERROR);
                int retryInterval = videoInfo.getRetryInterval();
                if (retryInterval > 0) {
                    log("replay delay " + retryInterval + " seconds");
                    handler.sendEmptyMessageDelayed(MSG_CTRL_RETRY, retryInterval * 1000);
                }
                return b;

            }
        });
        mediaPlayer.setOnSeekCompleteListener(new IMediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(IMediaPlayer iMediaPlayer) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        proxyListener().onSeekComplete(GiraffePlayer.this);
                    }
                });
            }
        });
        mediaPlayer.setOnVideoSizeChangedListener(new IMediaPlayer.OnVideoSizeChangedListener() {
            @Override
            public void onVideoSizeChanged(final IMediaPlayer mp, int width, int height, int sarNum, int sarDen) {
                if (debug) {
                    log("onVideoSizeChanged:width:" + width + ",height:" + height);
                }
                final int videoWidth = mp.getVideoWidth();
                final int videoHeight = mp.getVideoHeight();
//                int videoSarNum = mp.getVideoSarNum();
//                int videoSarDen = mp.getVideoSarDen();
                if (videoWidth != 0 && videoHeight != 0) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            proxyListener().onVideoSizeChanged(GiraffePlayer.this, videoWidth, videoHeight);
                        }
                    });
                }
            }
        });
        mediaPlayer.setOnTimedTextListener(new IMediaPlayer.OnTimedTextListener() {
            @Override
            public void onTimedText(IMediaPlayer mp, IjkTimedText text) {
                proxyListener().onTimedText(GiraffePlayer.this, text);
            }
        });
    }


    private void log(String msg) {
        if (debug) {
            Log.d(TAG, String.format("[fingerprint:%s] %s", videoInfo.getFingerprint(), msg));
        }
    }


    private synchronized void doRelease(String fingerprint) {
        if (released) {
            return;
        }
        log("doRelease");
        PlayerManager.getInstance().removePlayer(fingerprint);
        //1. quit handler thread
        internalPlaybackThread.quit();
        //2. release media player
        releaseMediaPlayer();
        //3. release surface
        if (surfaceTextureEntry != null) {
            surfaceTextureEntry.release();
            surfaceTextureEntry = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
        released = true;
    }

    /**
     * only release media player not display
     */
    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            log("releaseMediaPlayer");
            mediaPlayer.setSurface(null);
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    public void release() {
        log("try release");
        if (released) {
            return;
        }
        //不能异步执行，release之后Flutter的NativeView对象已经销毁，surface release时会出错
        String fingerprint = videoInfo.getFingerprint();
//        PlayerManager.getInstance().removePlayer(fingerprint);
//        handler.post(new Runnable() {
//            @Override
//            public void run() {
//                playerListener().onRelease(GiraffePlayer.this);
//            }
//        });
//        handler.obtainMessage(MSG_CTRL_RELEASE, fingerprint).sendToTarget();
        handler.removeCallbacks(null);
//        currentState(STATE_RELEASE);
        doRelease(fingerprint);
    }


    GiraffePlayer doMessage(Message message) {
        handler.sendMessage(message);
        return this;
    }


    void lazyLoadProgress(final int progress) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                proxyListener().onLazyLoadProgress(GiraffePlayer.this, progress);
            }
        });
    }

    public void lazyLoadError(final String message) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                proxyListener().onLazyLoadError(GiraffePlayer.this, message);
            }
        });
    }

    public void resume() {
        if (currentState == STATE_PAUSED) {
            targetState(STATE_PLAYING);
            handler.sendEmptyMessage(MSG_CTRL_PLAYING);
        }
//        playerListener().onPause(this);

    }

    class VideoViewAnimationListener {
        void onStart(ViewGroup src, ViewGroup target) {
        }

        void onEnd(ViewGroup src, ViewGroup target) {
        }
    }


    public void stop() {
        release();
    }

    public boolean isReleased() {
        return released;
    }


    public ITrackInfo[] getTrackInfo() {
        if (mediaPlayer == null || released) {
            return new ITrackInfo[0];
        }
        return mediaPlayer.getTrackInfo();
    }

    public int getSelectedTrack(int trackType) {
        if (mediaPlayer == null || released) {
            return -1;
        }
        if (mediaPlayer instanceof IjkMediaPlayer) {
            return ((IjkMediaPlayer) mediaPlayer).getSelectedTrack(trackType);
        } else if (mediaPlayer instanceof AndroidMediaPlayer) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return ((AndroidMediaPlayer) mediaPlayer).getInternalMediaPlayer().getSelectedTrack(trackType);
            }
        }
        return -1;
    }

    public GiraffePlayer selectTrack(int track) {
        if (mediaPlayer == null || released) {
            return this;
        }
        handler.removeMessages(MSG_CTRL_SELECT_TRACK);
        handler.obtainMessage(MSG_CTRL_SELECT_TRACK, track).sendToTarget();
        return this;
    }

    public GiraffePlayer deselectTrack(int selectedTrack) {
        if (mediaPlayer == null || released) {
            return this;
        }
        handler.removeMessages(MSG_CTRL_DESELECT_TRACK);
        handler.obtainMessage(MSG_CTRL_DESELECT_TRACK, selectedTrack).sendToTarget();
        return this;
    }

    /**
     * get current player state
     *
     * @return state
     */
    public int getCurrentState() {
        return currentState;
    }


    /**
     * set volume
     *
     * @param left  [0,1]
     * @param right [0,1]
     * @return GiraffePlayer
     */
    public GiraffePlayer setVolume(float left, float right) {
        if (mediaPlayer == null || released) {
            return this;
        }
        HashMap<String, Float> pram = new HashMap<>();
        pram.put("left", left);
        pram.put("right", right);
        handler.removeMessages(MSG_CTRL_SET_VOLUME);
        handler.obtainMessage(MSG_CTRL_SET_VOLUME, pram).sendToTarget();
        return this;
    }

    /**
     * set mute
     *
     * @param mute
     * @return GiraffePlayer
     */
    public GiraffePlayer setMute(boolean mute) {
        this.mute = mute;
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.setStreamMute(AudioManager.STREAM_MUSIC, mute);
        return this;
    }

    /**
     * is mute
     *
     * @return true if mute
     */
    public boolean isMute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            return audioManager.isStreamMute(AudioManager.STREAM_MUSIC);
        } else {
            return mute;
        }
    }

    /**
     * set looping play
     *
     * @param looping
     * @return
     */
    public GiraffePlayer setLooping(boolean looping) {
        if (mediaPlayer != null && !released) {
            mediaPlayer.setLooping(looping);
        }
        return this;
    }

    /**
     * @return is looping play
     */
    public boolean isLooping() {
        if (mediaPlayer != null && !released) {
            return mediaPlayer.isLooping();
        }
        return false;
    }

}
