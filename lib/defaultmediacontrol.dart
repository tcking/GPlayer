import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:gplayer/gplayer.dart';
import 'util.dart';

class DefaultMediaController with MediaController {
  double titlebarHeight;
  double bottombarHeight;
  String title;

  bool showTitleBar;
  bool gestureControlEnabled;

  /// 0:normal, 1:full screen,2:float window
  int _displayModel = 0;

  DefaultMediaController(
      {this.titlebarHeight = 32,
      this.bottombarHeight = 40,
      this.title = '',
      this.gestureControlEnabled =
          true, //enable gesture control,should disabled in scrollable container
      this.showTitleBar = true}) {
    print('=========DefaultMediaController============');
  }

  BuildContext context;

  @override
  Widget buildMediaController(BuildContext context) {
    this.context = context;
    return _ControllerWrapper(this);
  }

  @override
  void onCurrentStateChange({int newState, int oldState}) {
    // TODO: implement onCurrentStateChange
  }

  Orientation currentOrientation;

  enterFullScreen() async {
    SystemChrome.setEnabledSystemUIOverlays([]);
    currentOrientation = MediaQuery.of(context).orientation;
    if (player.landscapeWhenFullScreen &&
        currentOrientation == Orientation.portrait) {
      SystemChrome.setPreferredOrientations([
        DeviceOrientation.landscapeLeft,
        DeviceOrientation.landscapeRight,
      ]);
    }
    _displayModel = 1;
    updateUI();
    await Navigator.of(context).push(PageRouteBuilder(
      settings: RouteSettings(isInitialRoute: false),
      pageBuilder: (BuildContext context, Animation<double> animation,
          Animation<double> secondaryAnimation) {
        return new FadeTransition(
          opacity: animation,
          child: Scaffold(
            resizeToAvoidBottomPadding: false,
            body: Container(
              alignment: Alignment.center,
              color: Colors.black,
              child: player.display,
            ),
          ),
        );
      },
    ));
    SystemChrome.setEnabledSystemUIOverlays(SystemUiOverlay.values);
    if (player.landscapeWhenFullScreen &&
        currentOrientation == Orientation.portrait) {
      SystemChrome.setPreferredOrientations(
          [DeviceOrientation.portraitDown, DeviceOrientation.portraitUp]);
    }
    _displayModel = 0;
    updateUI();
  }

  exitFullScreen() {
    SystemChrome.setEnabledSystemUIOverlays(SystemUiOverlay.values);
    if (player.landscapeWhenFullScreen &&
        currentOrientation == Orientation.portrait) {
      SystemChrome.setPreferredOrientations([
        DeviceOrientation.portraitUp,
        DeviceOrientation.portraitDown,
      ]);
    }
    Navigator.of(context).pop();
    _displayModel = 0;
    updateUI();
  }

  @override
  void control(String action) {
    switch (action) {
      case 'toggleFloatWindow':
        if (player.displayModel == GPlayer.DISPLAY_MODEL_FLOAT) {
          _exitFloatWindow();
        } else {
          _floatWindow();
        }
        break;
      default:
        break;
    }
  }

  OverlayEntry _floatWindowEntry;

  void _floatWindow() {
    _floatWindowEntry?.remove();
    player.displayModel = GPlayer.DISPLAY_MODEL_FLOAT;
    var os = Overlay.of(context);
    _floatWindowEntry = OverlayEntry(builder: (context) {
      return _FloatWindow(this);
    });
    os.insert(_floatWindowEntry);
  }

  void _exitFloatWindow() {
    _floatWindowEntry?.remove();
    _floatWindowEntry = null;
    player.displayModel = GPlayer.DISPLAY_MODEL_NORMAL;
  }

  @override
  void dispose() {
  }
}

class _FloatWindow extends StatefulWidget {
  final DefaultMediaController controller;

  _FloatWindow(this.controller);

  @override
  State<StatefulWidget> createState() {
    return _FloatWindowState();
  }
}

class _FloatWindowState extends State<_FloatWindow> {
  double _bottom = 80;
  double _right = 80;

  @override
  initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return Positioned(
      bottom: _bottom,
      right: _right,
      child: Listener(
        onPointerMove: (_) {
          _bottom = _bottom - _.delta.dy;
          _right = _right - _.delta.dx;
          setState(() {});
        },
        child: Material(
          elevation: 5,
          child: Container(
            height: 120,
            width: 120,
//            decoration: BoxDecoration(boxShadow: [BoxShadow(
//                color: Colors.black,
//                blurRadius: 5.0, // has the effect of softening the shadow
//                spreadRadius: 5.0, // has the effect of extending the shadow
//                offset: Offset(
//                  10.0, // horizontal, move right 10
//                  10.0, // vertical, move down 10
//                ))]),
            child: Stack(
              children: <Widget>[
                widget.controller.player.innerDisplay,
                _buildCenter(context),
                Positioned(
                  top: 0,
                  right: 0,
                  child: GestureDetector(
                    onTapUp: (_) {
                      widget.controller._exitFloatWindow();
                    },
                    child: Padding(
                      padding: const EdgeInsets.all(4.0),
                      child: Icon(Icons.close, size: 16),
                    ),
                  ),
                )
              ],
            ),
          ),
        ),
      ),
    );
  }

  _buildCenter(BuildContext context) {
    var player = widget.controller.player;
    if (player.currentState == GPlayer.STATE_LAZYLOADING) {
      //show loading
      return Center(
          child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: <Widget>[
          Icon(
            Icons.cloud_download,
            size: 16,
            color: Colors.white,
          ),
          Text(
            'downloading decoder:${player.lazyLoadProgress}%',
            style: TextStyle(color: Colors.white),
          )
        ],
      ));
    } else if (player.currentState == GPlayer.STATE_ERROR) {
      //show loading
      return Center(
          child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: <Widget>[
          Icon(
            Icons.error_outline,
            size: 16,
            color: Colors.white,
          ),
          Text(
            player.error.message,
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.white),
          )
        ],
      ));
    } else if (player.currentState != GPlayer.STATE_PLAYBACK_COMPLETED &&
        player.currentState != player.targetState &&
        player.targetState == GPlayer.STATE_PLAYING) {
      //show loading
      return Center(child: CircularProgressIndicator());
    } else {
      //play button (show or hide)
      return Center(
        child: AnimatedOpacity(
          opacity: player.currentState == GPlayer.STATE_PAUSED ||
                  player.currentState == GPlayer.STATE_PLAYBACK_COMPLETED
              ? 1.0
              : 0.0,
          duration: Duration(milliseconds: 200),
          child: GestureDetector(
            onTap: () {
              player.start();
            },
            child: Container(
              decoration: BoxDecoration(
                color: Theme.of(context).dialogBackgroundColor,
                borderRadius: BorderRadius.circular(24.0),
              ),
              child: Padding(
                padding: EdgeInsets.all(12.0),
                child: Icon(Icons.play_arrow, size: 16.0),
              ),
            ),
          ),
        ),
      );
    }
  }
}

class ProgressSlider extends StatefulWidget {
  ProgressSlider({Key key}) : super(key: key);

  @override
  State<StatefulWidget> createState() {
    return _ProgressSliderState();
  }
}

class _ProgressSliderState extends State<ProgressSlider> {
  _ProgressSliderState() {
    print('======_PlayerSliderState=========');
  }

  DefaultMediaController controller;
  double _progress = 0;
  bool _isDraging = false;

  @override
  void didChangeDependencies() {
    controller = MediaController.of(context);
    super.didChangeDependencies();
  }

  @override
  Widget build(BuildContext context) {
    // TODO: implement build
    return Slider(
      onChanged: (double value) {
        setState(() {
          _progress = value;
        });
      },
      onChangeStart: (double value) {
        _isDraging = true;
        // controller._showThenHide(0);
        _ControllerWrapperState _s = context
            .ancestorStateOfType(const TypeMatcher<_ControllerWrapperState>());
        _s._showThenHide(0);
      },
      onChangeEnd: (double value) {
        // controller._showThenHide();
        _ControllerWrapperState _s = context
            .ancestorStateOfType(const TypeMatcher<_ControllerWrapperState>());
        _s._showThenHide();
        controller.player.seekTo(
            (controller.player.duration.inMilliseconds * value).round());
        _isDraging = false;
      },
      value: _isDraging ? _progress : controller.player.currentProgress,
      min: 0,
      max: 1.0,
    );
  }
}

class _ControllerWrapper extends StatefulWidget {
  final DefaultMediaController controller;

  _ControllerWrapper(this.controller);

  @override
  State<StatefulWidget> createState() {
    return _ControllerWrapperState();
  }
}

class _ControllerWrapperState extends State<_ControllerWrapper> {
  bool _show = false;
  bool _isDraging = false;
  Offset _dragStart;
  Offset _dragcurrent;
  double _dragDelta;
  int _dragDrection; //0:x,1:y
  int _controlType; //0:volume,1:brightness

  Timer _timer;
  Timer _getPlayerPostionTimer;

  dispose(){
    _timer?.cancel();
    _getPlayerPostionTimer?.cancel();
    super.dispose();
  }

  Widget _buildTitleBar() {
    return Container(
        color: Colors.black12,
        height: widget.controller.titlebarHeight,
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Expanded(
              child: Text(
                widget.controller.title,
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.white),
              ),
            ),
          ],
        ));
  }

  _buildCenterWhenDraging(BuildContext context) {
    //horizontal drag & duration > 0
    if (_dragDrection == 0 &&
        widget.controller.player.duration != null &&
        widget.controller.player.duration.inMilliseconds > 0) {
      RenderBox _rb = context.findRenderObject();

      var _size = _rb.size;
      var _ds = _dragDelta *
          widget.controller.player.duration.inSeconds /
          _size.width;
      var _dss = (_ds).toStringAsFixed(0) + 'S';
      return Center(
          child: Container(
        height: 48,
        width: 120,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: Colors.black54,
          borderRadius: BorderRadius.circular(8.0),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.center,
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Text(
              _ds > 0 ? '+$_dss' : _dss,
              style: TextStyle(color: Colors.white),
            ),
            Text(
              formatDuration(Duration(
                      milliseconds: (widget.controller.player
                              .currentPositionCache.inMilliseconds +
                          (_ds * 1000).floor()))) +
                  '/' +
                  formatDuration(widget.controller.player.duration),
              style: TextStyle(color: Colors.white),
            )
          ],
        ),
      ));
    } else if (_dragDrection == 1 && _dragDelta != null) {
      var _n; //新的值
      var _p; //ui上显示的百分比
      var _icon; //icon
      if (_controlType == 0) {
        _n = (_dragDelta / 180 * widget.controller.player.maxVolume).floor() +
            widget.controller.player.volume;
        if (_n > widget.controller.player.maxVolume) {
          _n = widget.controller.player.maxVolume;
        } else if (_n < 0) {
          _n = 0;
        }
        _p = _n == 0
            ? 'Off'
            : (_n / widget.controller.player.maxVolume * 100)
                    .toStringAsFixed(0) +
                '%';
        widget.controller.player.setStreamVolume(_n);
        _icon = _n == 0
            ? Icons.volume_off
            : (_n > 7 ? Icons.volume_up : Icons.volume_down);
      } else {
        _n = _dragDelta / 180 +
            (widget.controller.player.screenBrightness <= 0
                ? 0.5
                : widget.controller.player.screenBrightness);
        if (_n < 0.01) {
          _n = 0.01;
        } else if (_n > 1.0) {
          _n = 1.0;
        }
        _p = ((_n * 100) as double).toStringAsFixed(0) + '%';
        _icon = _n < 0.5
            ? Icons.brightness_low
            : (_n > 0.8 ? Icons.brightness_high : Icons.brightness_medium);
        widget.controller.player.setScreenBrightness(_n);
      }

      return Center(
        child: Container(
          height: 64,
          width: 64,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: Colors.black54,
            borderRadius: BorderRadius.circular(8.0),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: <Widget>[
              Icon(
                _icon,
                size: 32.0,
                color: Colors.white,
              ),
              Text(
                _p,
                style: TextStyle(color: Colors.white),
              )
            ],
          ),
        ),
      );
    } else {
      return Container();
    }
  }

  _buildCenter(BuildContext context) {
    if (_isDraging) {
      return _buildCenterWhenDraging(context);
    } else if (widget.controller.player.currentState ==
        GPlayer.STATE_LAZYLOADING) {
      //show loading
      return Center(
          child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: <Widget>[
          Icon(
            Icons.cloud_download,
            size: 32,
            color: Colors.white,
          ),
          Text(
            'downloading decoder:${widget.controller.player.lazyLoadProgress}%',
            style: TextStyle(color: Colors.white),
          )
        ],
      ));
    } else if (widget.controller.player.currentState == GPlayer.STATE_ERROR) {
      //show loading
      return Center(
          child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: <Widget>[
          Icon(
            Icons.error_outline,
            size: 32,
            color: Colors.white,
          ),
          Text(
            widget.controller.player.error.message,
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.white),
          )
        ],
      ));
    } else if (widget.controller.player.currentState !=
            GPlayer.STATE_PLAYBACK_COMPLETED &&
        widget.controller.player.currentState !=
            widget.controller.player.targetState &&
        widget.controller.player.targetState == GPlayer.STATE_PLAYING) {
      //show loading
      return Center(child: CircularProgressIndicator());
    } else {
      //play button (show or hide)
      return Center(
        child: AnimatedOpacity(
          opacity:
              widget.controller.player.currentState == GPlayer.STATE_PAUSED ||
                      widget.controller.player.currentState ==
                          GPlayer.STATE_PLAYBACK_COMPLETED
                  ? 1.0
                  : 0.0,
          duration: Duration(milliseconds: 200),
          child: GestureDetector(
            onTap: () {
              widget.controller.player.start();
            },
            child: Container(
              decoration: BoxDecoration(
                color: Theme.of(context).dialogBackgroundColor,
                borderRadius: BorderRadius.circular(48.0),
              ),
              child: Padding(
                padding: EdgeInsets.all(12.0),
                child: Icon(Icons.play_arrow, size: 32.0),
              ),
            ),
          ),
        ),
      );
    }
  }

  Widget _buildBottomBar() {
    _buildLeftButton() {
      return GestureDetector(
        onTap: () {
          if (widget.controller.player.targetState == GPlayer.STATE_PLAYING) {
            widget.controller.player.pause();
          } else {
            widget.controller.player.start();
          }
        },
        child: Container(
          height: widget.controller.bottombarHeight,
          color: Colors.transparent,
          // margin: EdgeInsets.only(left: 4.0, right: 4.0),
          padding: EdgeInsets.only(
            left: 4.0,
            right: 4.0,
          ),
          child: Icon(
            widget.controller.player.targetState == GPlayer.STATE_PLAYING
                ? Icons.pause
                : Icons.play_arrow,
            color: Colors.white,
          ),
        ),
      );
    }

    _buildCurrentPoition() {
      return Padding(
          padding: EdgeInsets.only(left: 4, right: 4),
          child: Text(
            formatDuration(
              widget.controller.player.currentPositionCache,
            ),
            style: TextStyle(color: Colors.white),
          ));
    }

    _buildProgressBar() {
      return Expanded(
        child: widget.controller.player.isLive?Container():ProgressSlider(),
      );
    }

    _buildDuration() {
      return Padding(
          padding: EdgeInsets.only(left: 4, right: 4),
          child: Text(
            widget.controller.player.isLive?'Live':formatDuration(
              widget.controller.player.duration,
            ),
            style: TextStyle(color: Colors.white),
          ));
    }

    _buildFullScreenToggle() {
      return GestureDetector(
        onTap: () {
          if (widget.controller._displayModel == 0) {
            widget.controller.enterFullScreen();
          } else {
            widget.controller.exitFullScreen();
          }
        },
        child: Container(
          height: widget.controller.bottombarHeight,
          color: Colors.transparent,
          // margin: EdgeInsets.only(left: 4.0, right: 4.0),
          padding: EdgeInsets.only(
            left: 4.0,
            right: 4.0,
          ),
          child: Icon(
            widget.controller._displayModel == 0
                ? Icons.fullscreen
                : Icons.fullscreen_exit,
            color: Colors.white,
          ),
        ),
      );
    }

    return Container(
      color: Colors.black12,
      height: widget.controller.bottombarHeight,
      child: Row(
        children: <Widget>[
          _buildLeftButton(),
          _buildCurrentPoition(),
          _buildProgressBar(),
          _buildDuration(),
          _buildFullScreenToggle(),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    var children = <Widget>[];
    if (widget.controller.showTitleBar) {
      children.add(_buildTitleBar());
    }
    children.add(Expanded(
      child: Container(),
    )); //expand the left space
    children.add(_buildBottomBar());

    return new GestureDetector(
        onTap: () {
          _showThenHide();
        },
        onHorizontalDragStart: !widget.controller.gestureControlEnabled
            ? null
            : (_) {
                _isDraging = true;
                _dragStart = _.globalPosition;
                _dragDrection = 0;
                print('onHorizontalDragStart:$_');
              },
        onHorizontalDragEnd: !widget.controller.gestureControlEnabled
            ? null
            : (_) {
                _isDraging = false;
                RenderBox _rb = context.findRenderObject();

                var _size = _rb.size;
                var _ds = _dragDelta *
                    widget.controller.player.duration.inSeconds /
                    _size.width;

                var _n = widget
                        .controller.player.currentPositionCache.inMilliseconds +
                    (_ds * 1000).floor();
                widget.controller.player.seekTo(_n);

                print('onHorizontalDragEnd:$_');
                updateUI();
              },
        onVerticalDragStart: !widget.controller.gestureControlEnabled
            ? null
            : (_) async {
                _isDraging = true;
                _dragStart = _.globalPosition;
                _dragDrection = 1;

                RenderBox _rb = context.findRenderObject();
                final topLeftPosition = _rb.localToGlobal(Offset.zero);
                if (topLeftPosition.dx + _dragStart.dx < _rb.size.width / 2) {
                  _controlType = 0;
                } else {
                  _controlType = 1;
                }
                await widget.controller.player.getAllInfo();
                print('onVerticalDragStart:$_');
              },
        onVerticalDragEnd: !widget.controller.gestureControlEnabled
            ? null
            : (_) {
                _isDraging = false;
                print('onVerticalDragEnd:$_');
                updateUI();
              },
        onHorizontalDragUpdate: !widget.controller.gestureControlEnabled
            ? null
            : (_) {
                _dragcurrent = _.globalPosition;
                _dragDelta = _dragcurrent.dx - _dragStart.dx;
                print('onHorizontalDragUpdate:$_dragDelta');

                updateUI();
              },
        onVerticalDragUpdate: !widget.controller.gestureControlEnabled
            ? null
            : (_) {
                // print('onVerticalDragUpdate:${_.globalPosition}');
                _dragcurrent = _.globalPosition;
                _dragDelta = _dragStart.dy - _dragcurrent.dy;
                // print('onVerticalDragUpdate:$_dragDelta');

                updateUI();
              },
        child: FractionallySizedBox(
          widthFactor: 1.0,
          heightFactor: 1.0,
          child: Stack(
            children: <Widget>[
              _show
                  ? Column(
                      children: children,
                    )
                  : Container(
                      color: Colors.transparent,
                    ),
              _buildCenter(context)
            ],
          ),
        ));
  }

  _showThenHide([int hideDelay = 2000]) {
    _timer?.cancel();
    _show = true;

    _getPlayerPostionTimer = Timer.periodic(
      const Duration(milliseconds: 500),
      (Timer timer) async {
        await widget.controller.player.currentPosition;
        updateUI();
      },
    );

    updateUI();

    if (hideDelay > 0) {
      _timer = new Timer(Duration(milliseconds: hideDelay), () {
        _show = false;
        _getPlayerPostionTimer?.cancel();
        updateUI();
      });
    }
  }

  updateUI() {
    if (mounted) {
      setState(() {});
    }
  }
}
