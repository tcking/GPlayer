import 'dart:async';

// import 'dart:io';
import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import 'package:uuid/uuid.dart';
import 'defaultmediacontrol.dart';


class GPlayer {
  // all possible internal states
  static const int STATE_ERROR = -1;
  static const int STATE_IDLE = 0;
  static const int STATE_PREPARING = 1;
  static const int STATE_PREPARED = 2;
  static const int STATE_PLAYING = 3;
  static const int STATE_PAUSED = 4;
  static const int STATE_PLAYBACK_COMPLETED = 5;
  static const int STATE_RELEASE = 6;
  static const int STATE_LAZYLOADING = 7;

  static const int DISPLAY_MODEL_NORMAL = 0;
  static const int DISPLAY_MODEL_FLOAT = 2;

  int _displayModel = DISPLAY_MODEL_NORMAL;

  get displayModel {
    return _displayModel;
  }

  set displayModel(model) {
    _displayModel = model;
    updateUI();
  }

  final MethodChannel _channel =
      const MethodChannel('com.github.tcking/gplayer');
  MediaController mediaController;

  final List<Function(Map params)> _listeners = [];

  int _textureId;

  /// player fingerprint
  final String fingerprint = Uuid().v4();

  /// source uri
  String uri;

  //the display view aspect ratio,<=0 means follow the video
  double aspectRatio;

  /// player current status
  int currentState = 0;

  /// player target status
  int targetState = 0;

  /// player implementation ijk:ijkplayer,system:native system player
  String playerImpl;

  /// video duration
  Duration duration = Duration(milliseconds: 0);

  /// lazy load progress
  int lazyLoadProgress = 0;

  /// the settting options of player ,eg:header of agent
  List<Option> options;

  /// the cache of current postion
  Duration currentPositionCache = Duration();

  /// is the video is live

  bool isLive = false;
  bool landscapeWhenFullScreen;
  bool looping;

  Error error;

  Color backgroudColor;

  _LifeCycleObserver _lifeCycleObserver;

  //-------------------------

  bool get isPlaying {
    return currentState != STATE_ERROR &&
        currentState != STATE_PLAYBACK_COMPLETED &&
        targetState == STATE_PLAYING;
  }

  void addListener(Function(Map params) fn) {
    _listeners.add(fn);
  }

  void removeListener(Function(Map params) fn) {
    _listeners.remove(fn);
  }

  void _emitListeners(Map params) {
    for (var fn in _listeners) {
      fn(params);
    }
  }

  PlayerDisplayState _currentState;

  void updateUI() {
    // key?.currentState?.updateUI();
    _currentState?.updateUI();
  }

  GPlayer(
      {@required this.uri,
      this.playerImpl = 'ijk',
      this.options = const [],
      this.looping = false,
      this.aspectRatio = -1,
      this.landscapeWhenFullScreen = true,
      this.mediaController,
      this.backgroudColor = Colors.black26}) {
    print('new GPlayer instance');

    this.mediaController ??= DefaultMediaController();
    this.mediaController.bind(this);
  }

  /// get the current position in the current video.
  Future<Duration> get currentPosition async {
    int ms = await _invokeMethod('getCurrentPosition');
    currentPositionCache = Duration(milliseconds: ms);
    return Duration(milliseconds: ms);
  }

  double get currentProgress {
    if (duration == null ||
        currentPositionCache == null ||
        duration.inMilliseconds == 0) {
      return 0;
    }
    return currentPositionCache.inMilliseconds / duration.inMilliseconds;
  }

  Future<void> init() async {
    _lifeCycleObserver = _LifeCycleObserver(player: this);
    await _invokeMethod('init', {
      'uri': this.uri,
      'looping': looping,
      'playerImpl': playerImpl,
      'options': options.fold(<Map>[], (List<Map> prev, Option element) {
        prev.add(element.asMap());
        return prev;
      })
    });
    _initEventChannel();
  }

  _initEventChannel() {
    new EventChannel('com.github.tcking/gplayer/$fingerprint')
        .receiveBroadcastStream()
        .listen(_onGetPlayerData, onError: _onGetPlayerError);
    print('_initEventChannel:com.github.tcking/gplayer/$fingerprint');
  }

  void _onGetPlayerData(dynamic event) {
    final Map<dynamic, dynamic> params = event;
    final String e = params['event'];
    print('onGetPlayerData');
    print(params);
    switch (e) {
      case 'onSetDisplay':
        _textureId = params['textureId'];
        break;
      case 'onPrepared':
        duration = Duration(milliseconds: params['duration']);
        isLive = duration.inMilliseconds == 0;
        break;
      case 'onLazyLoadProgress':
        currentState = STATE_LAZYLOADING;
        lazyLoadProgress = params['progress'];
        break;
      case 'onLazyLoadError':
        currentState = STATE_ERROR;
        error = Error(msg: params['message']);
        break;
      case 'onCurrentStateChange':
        currentState = params['newState'];
        this.mediaController.onCurrentStateChange(
            newState: currentState, oldState: params['oldState']);
        break;
      case 'onTargetStateChange':
        targetState = params['newState'];
        break;
      case 'onSeekComplete':
        currentPositionCache = Duration(milliseconds: params['position']);
        break;
      case 'onError':
        error = Error(what: params['what'], extra: params['what']);
        break;
      case 'onVideoSizeChanged':
        int videoWidth = params['videoWidth'];
        int videoHeight = params['videoHeight'];
        if (this.aspectRatio <= 0) {
          this.aspectRatio = videoWidth / videoHeight;
        }
        break;
      default:
    }
    mediaController.updateUI();
    _emitListeners(params);
  }

  void _onGetPlayerError(dynamic e) {
    print('_onGetPlayerError:$e');
  }

  void seekTo(int pos) async {
    currentPositionCache = Duration(milliseconds: pos);
    await _invokeMethod('seekTo', {'position': pos});
  }

  int maxVolume = 15;
  int volume = 7;
  double screenBrightness = 0.5;

  Future<Map> getAllInfo() async {
    var _rsp = await _invokeMethod('getAllInfo');
    maxVolume = _rsp['maxVolume'];
    volume = _rsp['volume'] < 0 ? 0 : _rsp['volume'];
    currentPositionCache = Duration(milliseconds: _rsp['currentPosition']);
    screenBrightness = _rsp['screenBrightness'];
    print(
        'maxVolume:$maxVolume,volume:$volume,screenBrightness:$screenBrightness');
    return _rsp;
  }

  //返回播放的view
  Widget get display {
    return PlayerDisplay(
      player: this,
      mediaController: mediaController,
    );
  }

  /// video display without media controller
  Widget get  innerDisplay {
    return _textureId == null
        ? Container(
      color: backgroudColor,
    )
        : Center(
      child: aspectRatio >= 0
          ? AspectRatio(
        aspectRatio: aspectRatio,
        child: Texture(
          textureId: _textureId,
        ),
      )
          : Texture(
        textureId:_textureId,
      ),
    );
  }

  Future<void> pause() async {
    return await _invokeMethod('pause');
  }

  Future<void> start() async {
    return await _invokeMethod('start');
  }

  Future<void> setStreamVolume(int volume) async {
    return await _invokeMethod('setStreamVolume', {'volume': volume});
  }

  Future<void> setScreenBrightness(double brightness) async {
    return await _invokeMethod(
        'setScreenBrightness', {'brightness': brightness});
  }

  Future<T> _invokeMethod<T>(String method,
      [Map<String, dynamic> params]) async {
    params ??= {};
    params?.putIfAbsent('fingerprint', () => fingerprint);
    return await _channel.invokeMethod(method, params);
  }

  void dispose() {
    _invokeMethod('release');
    mediaController?.dispose();
    _lifeCycleObserver?.dispose();
  }
}

class Option {
  final int category;
  final String name;
  final value;

  Option(this.category, this.name, this.value);

  Map asMap() {
    return {'category': category, 'name': name, 'value': value};
  }
}

abstract class MediaController {
  GPlayer player;

  void bind(GPlayer player) {
    this.player = player;
  }

  /// 方便子widget获取controller和player
  static of(BuildContext context) {
    final MediaControllerProvider provider =
        context.inheritFromWidgetOfExactType(MediaControllerProvider);

    return provider.controller;
  }

  void updateUI() {
    player.updateUI();
  }

  void dispose();

  /// a common entry to control the player
  void control(String action);

  void onCurrentStateChange({int newState, int oldState});

  ///build media controller
  Widget buildMediaController(BuildContext context);
}

class Error {
  int what;
  int extra;
  String msg;

  Error({this.what, this.extra, this.msg});

  String get message {
    if (msg != null) {
      return msg;
    }

    return 'error';
  }
}

class PlayerDisplay extends StatefulWidget {
  final GPlayer player;
  final MediaController mediaController;

  PlayerDisplay({Key key, this.player, this.mediaController}) : super(key: key);

  @override
  State<StatefulWidget> createState() {
    print('-----PlayerDisplay------');
    player._currentState = PlayerDisplayState();
    return player._currentState;
  }
}

class PlayerDisplayState extends State<PlayerDisplay> {
  updateUI() {
    if (mounted) {
      setState(() {});
    }
  }

  @override
  Widget build(BuildContext context) {
    return MediaControllerProvider(
      controller: widget.mediaController,
      child: widget.player.displayModel == GPlayer.DISPLAY_MODEL_FLOAT
          ? Container(color: widget.player.backgroudColor,)
          : Stack(overflow: Overflow.visible, children: [
              widget.player.innerDisplay,
              widget.mediaController.buildMediaController(context)
            ]),
    );
  }


}

class MediaControllerProvider extends InheritedWidget {
  final MediaController controller;
  final Widget child;

  MediaControllerProvider(
      {Key key, @required this.controller, @required this.child})
      : super(key: key, child: child);

  @override
  bool updateShouldNotify(MediaControllerProvider oldWidget) {
    return controller != oldWidget.controller;
  }
}

class _LifeCycleObserver with WidgetsBindingObserver {
  final GPlayer player;
  int _targetStatus;

  _LifeCycleObserver({this.player}) {
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.paused:
      case AppLifecycleState.inactive:
        if (player.isPlaying) {
          _targetStatus = GPlayer.STATE_PLAYING;
          player.pause();
        }
        break;
      case AppLifecycleState.resumed:
        if (_targetStatus == GPlayer.STATE_PLAYING) {
          player.start();
          _targetStatus = null;
        }
        break;
      default:
    }
  }

  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
  }
}
