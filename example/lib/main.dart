import 'package:flutter/material.dart';
import 'package:gplayer/defaultmediacontrol.dart';
import 'package:gplayer/gplayer.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  GPlayer player;

  @override
  void initState() {
    super.initState();
    player = GPlayer(
        uri: 'http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4',
        options: [Option(1, 'multiple_requests', 1)],
        mediaController: DefaultMediaController(
            title: 'bunny bear', gestureControlEnabled: true))
      ..init()
      ..addListener((_) {
        //update control button out of player
        setState(() {});
      });
  }

  void didUpdateWidget(oldWidget) {
    print('didUpdateWidget---------');
    super.didUpdateWidget(oldWidget);
  }

  @override
  void dispose() {
    super.dispose();
    player?.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Video Demo',
      routes: {'videoList': (context) => VideoList()},
      home: Scaffold(
        appBar: AppBar(
          title: Text('GPlayer'),
        ),
        body: Builder(
          builder: (context) =>
              Padding(
                padding: EdgeInsets.only(top: 0),
                child: ListView(
                  children: <Widget>[
                    AspectRatio(
                      aspectRatio: 16 / 9,
                      child: player.display,
                    ),
                    Padding(
                      padding: EdgeInsets.all(18),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: <Widget>[
                          Row(
                            children: <Widget>[
                              Text('landscapeWhenFullScreen:'),
                              Switch(
                                  value: player.landscapeWhenFullScreen,
                                  onChanged: (_) {
                                    player.landscapeWhenFullScreen = _;
                                    setState(() {});
                                  })
                            ],
                          ),
                          RaisedButton.icon(
                              onPressed: () {
                                player?.mediaController
                                    ?.control('toggleFloatWindow');
                              },
                              icon: Icon(Icons.featured_video),
                              label: Text("toggle float window")),
                          RaisedButton.icon(
                              onPressed: () {
                                if (player.isPlaying) {
                                  player.pause();
                                }
                                Navigator.pushNamed(context, 'videoList');
                              },
                              icon: Icon(Icons.list),
                              label: Text("video in list"))
                        ],
                      ),
                    ),
                  ],
                ),
              ),
        ),
        floatingActionButton: FloatingActionButton(
          onPressed: () {
            setState(() {
              player.isPlaying ? player.pause() : player.start();
            });
          },
          child: Icon(
            player.isPlaying ? Icons.pause : Icons.play_arrow,
          ),
        ),
      ),
    );
  }
}

class GPlayerHolder extends StatefulWidget {
  final String url;
  final String title;

  GPlayerHolder({this.url, this.title});

  @override
  State<StatefulWidget> createState() {
    // TODO: implement createState
    return GPlayerHolderState();
  }
}

class GPlayerHolderState extends State<GPlayerHolder> {
  GPlayer player;

  @override
  void initState() {
    player = GPlayer(
        uri: widget.url,
        mediaController: DefaultMediaController(
            title: widget.title, gestureControlEnabled: false))
      ..init();
    super.initState();
  }


  @override
  void dispose() {
    player?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AspectRatio(
      aspectRatio: 16 / 9,
      child: player.display,
    );
  }
}

class VideoList extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    Widget divider1 = Divider(
      color: Colors.blue,
    );
    Widget divider2 = Divider(color: Colors.green);

    const videos = [
      'http://zhibo.hkstv.tv/livestream/zb2yhapo/playlist.m3u8',
      'http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4',
      'rtmp://live.hkstv.hk.lxdns.com/live/hks2',
      'http://zhibo.hkstv.tv/livestream/zb2yhapo/playlist.m3u8',
      'http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4',
      'rtmp://live.hkstv.hk.lxdns.com/live/hks2'
    ];

    return Scaffold(
      appBar: AppBar(
        title: Text('video list'),
      ),
      body: ListView.separated(
          shrinkWrap: true,
          itemCount: videos.length,
          itemBuilder: (context, index) {
            return GPlayerHolder(
              title: 'video $index',
              url: videos[index],
            );
          },
          separatorBuilder: (BuildContext context, int index) {
            return index % 2 == 0 ? divider1 : divider2;
          }),
    );
  }
}
