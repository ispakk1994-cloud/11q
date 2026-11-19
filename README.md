# oneone_q_widget

11Q 앱의 홈 화면 위젯(안드로이드 · 갤럭시)을 담은 Flutter 플러그인이에요.

- 위젯 A · 오늘 할 일: 대시보드와 같은 목록 · 체크칸으로 끝냄 · ＋ = 홈 화면 위 작은 창으로 적기(앱은 안 열림) · 앱 테마를 따라감 · 크기 조절.
- 아이폰 · 웹에서는 아무것도 하지 않아요.
- 비밀 정보(키 · 토큰 · 서버 주소)는 넣지 않아요. 목록 · 끝내기는 앱 코드가 넘겨줘요.

## FlutterFlow에 넣기

App Settings › Project Dependencies › Custom Pub Dependencies › Add Pub Dependency
(`Use a custom source` 스위치를 켜고)

```yaml
oneone_q_widget:
  git:
    url: https://github.com/ispakk1994-cloud/11q.git
    ref: 45f42aa18ccdf69f94f24a58a09f1b4436487024
```

FlutterFlow 코드 편집기는 의존성을 처음 받은 판으로 기억해요(`ref`가 없으면 새 판을 올려도 편집기는 옛 판으로 검사해 "isn't defined" 오류). 그래서 늘 `ref: <커밋 번호>`로 묶고, 새 판이 나오면 이 번호만 바꿔요. 판 번호: 0.1.0 = `1bf95df` · 0.1.1 = `51b9d33` · 0.2.0 = `4ab24b7` · 0.3.0 = `45f42aa`.

## Dart

```dart
import 'package:oneone_q_widget/oneone_q_widget.dart';

await OneoneQWidget.save(feedJson);                 // 위젯이 그릴 목록(JSON)
await OneoneQWidget.setBackgroundHandler(handler);  // 체크 · 15분 확인 · ＋ 작은 창 넣기 때 화면 없이 부를 함수(글자를 돌려주면 작은 창이 받음)
final a = await OneoneQWidget.takeAction();         // 작은 창 `앱에서 적기`면 'add' · 작은 창 카메라면 'camera'
OneoneQWidget.actions.listen((_) { /* 앱이 켜져 있을 때 ＋ */ });
```

목록 JSON: `{uid, lang, title, count, empty, theme:{mode, ink, meta, accent, …}, rows:[{t:'h', text, tone} | {t:'r', id, title, now, nowText, flag, flagText, meta, over, rec}]}`

작은 창 색(theme 안 · 없으면 유리): `sheet` · `line` · `bw` · `dlgR` · `field` · `fieldLine` · `ctlR` · `ctl` · `ctlLine` · `ctlInk` · `accentLine` · `alert` · `link` · `pillR` · `shadow` · `shX` · `shY` · `shBlur`.
