package com.github.tcking.gplayer;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Created by tcking on 2017
 */

public class VideoInfo implements Parcelable {
    public static final String PLAYER_IMPL_IJK = "ijk";
    public static final String PLAYER_IMPL_SYSTEM = "system";


    private HashSet<Option> options = new HashSet<>();
    private Uri uri;
    private String fingerprint = Integer.toHexString(hashCode());
    private String lastFingerprint;
    private Uri lastUri;
    private int retryInterval = 0;
    private String playerImpl = PLAYER_IMPL_IJK;
    private boolean looping = false;

//    public VideoInfo(VideoInfo defaultVideoInfo) {
//        title = defaultVideoInfo.title;
//        for (Option op : defaultVideoInfo.options) {
//            try {
//                options.add(op.clone());
//            } catch (CloneNotSupportedException e) {
//                e.printStackTrace();
//            }
//        }
//        showTopBar = defaultVideoInfo.showTopBar;
//        retryInterval = defaultVideoInfo.retryInterval;
//        bgColor = defaultVideoInfo.bgColor;
//        playerImpl = defaultVideoInfo.playerImpl;
//        fullScreenAnimation = defaultVideoInfo.fullScreenAnimation;
//        looping = defaultVideoInfo.looping;
//        currentVideoAsCover = defaultVideoInfo.currentVideoAsCover;
//        fullScreenOnly = defaultVideoInfo.fullScreenOnly;
//
//    }

    public static VideoInfo from(Map arguments) {
        String fingerprint = (String) arguments.get("fingerprint");
        VideoInfo videoInfo = new VideoInfo();
        videoInfo.fingerprint = fingerprint;
        videoInfo.setUri(Uri.parse((String) arguments.get("uri")));
        videoInfo.looping = (boolean) arguments.get("looping");
        videoInfo.playerImpl = (String) arguments.get("playerImpl");
        videoInfo.options.addAll(Option.from((List<Map>) arguments.get("options")));

        return videoInfo;
    }


    public String getPlayerImpl() {
        return playerImpl;
    }

    public VideoInfo setPlayerImpl(String playerImpl) {
        this.playerImpl = playerImpl;
        return this;
    }




    public int getRetryInterval() {
        return retryInterval;
    }

    /**
     * retry to play again interval (in second)
     *
     * @param retryInterval interval in second <=0 will disable retry
     * @return VideoInfo
     */
    public VideoInfo setRetryInterval(int retryInterval) {
        this.retryInterval = retryInterval;
        return this;
    }


    public HashSet<Option> getOptions() {
        return options;
    }

    /**
     * add player init option
     *
     * @param option option
     * @return VideoInfo
     */
    public VideoInfo addOption(Option option) {
        this.options.add(option);
        return this;
    }

    /**
     * add player init option
     *
     * @return VideoInfo
     */
    public VideoInfo addOptions(Collection<Option> options) {
        this.options.addAll(options);
        return this;
    }





    public VideoInfo() {
    }

    public VideoInfo(Uri uri) {
        this.uri = uri;
    }

    public VideoInfo(String uri) {
        this.uri = Uri.parse(uri);
    }

    protected VideoInfo(Parcel in) {
        fingerprint = in.readString();
        uri = in.readParcelable(Uri.class.getClassLoader());
        lastFingerprint = in.readString();
        lastUri = in.readParcelable(Uri.class.getClassLoader());
        options = (HashSet<Option>) in.readSerializable();
        retryInterval = in.readInt();
        playerImpl = in.readString();
        looping = in.readByte() != 0;
    }

    public static final Creator<VideoInfo> CREATOR = new Creator<VideoInfo>() {
        @Override
        public VideoInfo createFromParcel(Parcel in) {
            return new VideoInfo(in);
        }

        @Override
        public VideoInfo[] newArray(int size) {
            return new VideoInfo[size];
        }
    };


    public VideoInfo setFingerprint(Object fingerprint) {
        String fp = "" + fingerprint;//to string first
        if (lastFingerprint != null && !lastFingerprint.equals(fp)) {
            //different from last setFingerprint, release last
//            PlayerManager.getInstance().releaseByFingerprint(lastFingerprint);
        }
        this.fingerprint = fp;
        lastFingerprint = this.fingerprint;
        return this;
    }

    /**
     * A Fingerprint represent a player
     *
     * @return setFingerprint
     */
    public String getFingerprint() {
        return fingerprint;
    }

    public Uri getUri() {
        return uri;
    }

    /**
     * set video uri
     *
     * @param uri uri
     * @return VideoInfo
     */
    public VideoInfo setUri(Uri uri) {
        if (lastUri != null && !lastUri.equals(uri)) {
            //different from last uri, release last
//            PlayerManager.getInstance().releaseByFingerprint(fingerprint);
        }
        this.uri = uri;
        this.lastUri = this.uri;
        return this;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(fingerprint);
        dest.writeParcelable(uri, flags);
        dest.writeString(lastFingerprint);
        dest.writeParcelable(lastUri, flags);
        dest.writeSerializable(options);
        dest.writeInt(retryInterval);
        dest.writeString(playerImpl);
        dest.writeByte((byte) (looping ? 1 : 0));
    }

    public static VideoInfo createFromDefault() {
        return new VideoInfo();
    }

    public boolean isLooping() {
        return looping;
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

}
