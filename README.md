# oneone_q_widget

11Q 앱의 홈 화면 위젯(안드로이드 · 갤럭시)을 담은 Flutter 플러그인이에요.

- 위젯 A · 오늘 할 일: 대시보드와 같은 목록 · 체크칸으로 끝냄 · ＋로 적기 · 앱 테마를 따라감 · 크기 조절.
- 아이폰 · 웹에서는 아무것도 하지 않아요.
- 비밀 정보(키 · 토큰 · 서버 주소)는 넣지 않아요. 목록 · 끝내기는 앱 코드가 넘겨줘요.

## FlutterFlow에 넣기

App Settings › Project Dependencies › Custom Pub Dependencies › Add Pub Dependency
(`Use a custom source` 스위치를 켜고)

```yaml
oneone_q_widget:
  git:
    url: https://github.com/ispakk1994-cloud/11q.git
```

`ref`를 안 쓰면 늘 main의 마지막 판을 받아요. 특정 판으로 묶으려면 `ref: <커밋 번호>`(예: 0.1.0 = `1bf95df`).

## Dart

```dart
import 'package:oneone_q_widget/oneone_q_widget.dart';

await OneoneQWidget.save(feedJson);                 // 위젯이 그릴 목록(JSON)
await OneoneQWidget.setBackgroundHandler(handler);  // 체크 · 30분 갱신 때 화면 없이 부를 함수
final a = await OneoneQWidget.takeAction();         // ＋로 열렸으면 'add'
OneoneQWidget.actions.listen((_) { /* 앱이 켜져 있을 때 ＋ */ });
```

목록 JSON: `{title, count, empty, theme:{mode, ink, meta, accent, …}, rows:[{t:'h', text, tone} | {t:'r', id, title, now, nowText, flag, flagText, meta, over, rec}]}`
