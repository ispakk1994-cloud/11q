# oneone_q_widget

11Q 앱의 홈 화면 위젯(안드로이드 · 갤럭시)과 아이폰 위젯 통로를 담은 Flutter 플러그인이에요.

- 위젯 A · 오늘 할 일: 대시보드와 같은 목록 · 체크칸으로 끝냄 · ＋ = 홈 화면 위 작은 창으로 적기(앱은 안 열림) · 앱 테마를 따라감 · 크기 조절.
- 아이폰(0.5.0 ~): 목록을 앱 그룹 저장소에 두고 위젯을 다시 그리게 하는 통로 · ＋ 주소(`oneoneq://widget/add`) 받기 · 0.6.0부터 위젯 체크 · 바로 적기 · 뒤에서 확인을 화면 없는 엔진으로(앱 코드). 위젯 화면 · App Intent는 앱 쪽에서 붙여요.
- 웹에서는 아무것도 하지 않아요.
- 비밀 정보(키 · 토큰 · 서버 주소)는 넣지 않아요. 목록 · 끝내기는 앱 코드가 넘겨줘요.

## FlutterFlow에 넣기

App Settings › Project Dependencies › Custom Pub Dependencies › Add Pub Dependency
(`Use a custom source` 스위치를 켜고)

```yaml
oneone_q_widget:
  git:
    url: https://github.com/ispakk1994-cloud/11q.git
    ref: 3f59af05a6e654cbaf873bec2599024dcb5f9296
```

FlutterFlow 코드 편집기는 의존성을 처음 받은 판으로 기억해요(`ref`가 없으면 새 판을 올려도 편집기는 옛 판으로 검사해 "isn't defined" 오류). 그래서 늘 `ref: <커밋 번호>`로 묶고, 새 판이 나오면 이 번호만 바꿔요. 판 번호: 0.1.0 = `1bf95df` · 0.1.1 = `51b9d33` · 0.2.0 = `4ab24b7` · 0.3.0 = `45f42aa` · 0.4.0 = `0dcbb4b` · 0.5.0 = `d3a07c9`(아이폰 통로) · 0.6.0 = `52eee6f`(아이폰 체크 · 바로 적기 · 뒤에서 확인) · 0.6.1 = `3f59af0`(안드로이드 받아쓰기 서비스 찾기) · 0.3.1 = `2f27071`(1.1용 · 가지 `rel-0.3` · 0.3.0 + 받아쓰기 서비스 찾기).

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
