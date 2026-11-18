# oneone_q_widget

11Q 앱의 홈 화면 위젯(안드로이드 · 갤럭시)을 담은 Flutter 플러그인이에요.

- 0.0.1: 빈 위젯 시험. 위젯 목록에 `11Q · 오늘 할 일`이 뜨고, 놓으면 11Q 카드가 보이며, 누르면 앱이 열려요.
- 아이폰 · 웹에서는 아무것도 하지 않아요.
- 비밀 정보(키 · 토큰 · 서버 주소)는 넣지 않아요.

## FlutterFlow에 넣기

App Settings › Project Dependencies › Custom Pub Dependencies › Add Pub Dependency

```yaml
oneone_q_widget:
  git:
    url: https://github.com/ispakk1994-cloud/11q.git
    ref: main
```

## Dart

```dart
import 'package:oneone_q_widget/oneone_q_widget.dart';

await OneoneQWidget.ping();    // 'ok'
await OneoneQWidget.count();   // 홈 화면에 놓인 위젯 수
await OneoneQWidget.refresh(); // 다시 그리기
```
