import 'dart:async';
import 'dart:ui' show CallbackHandle, DartPluginRegistrant, PluginUtilities;

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart' show WidgetsFlutterBinding;

/// 11Q 홈 화면 위젯(안드로이드 · 갤럭시).
///
/// 아이폰 · 웹(FlutterFlow 테스트 모드)에서는 아무것도 하지 않고 null을 돌려준다.
/// 그래서 FlutterFlow 커스텀 코드에서 플랫폼을 따로 가리지 않고 불러도 된다.
class OneoneQWidget {
  OneoneQWidget._();

  static const MethodChannel _channel = MethodChannel('oneone_q_widget');
  static final StreamController<String> _actions =
      StreamController<String>.broadcast();
  static bool _listening = false;

  /// 이 기기에서 위젯을 쓸 수 있는지(안드로이드 앱일 때만 true).
  static bool get isSupported =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  /// 플러그인이 붙었는지 확인. 붙었으면 'ok'.
  static Future<String?> ping() => _call<String>('ping');

  /// 홈 화면에 놓인 11Q 위젯 수.
  static Future<int?> count() => _call<int>('count');

  /// 놓인 위젯을 모두 다시 그린다. 다시 그린 위젯 수를 돌려준다.
  static Future<int?> refresh() => _call<int>('refresh');

  /// 홈 화면에 위젯을 놓아 달라고 런처에 요청한다(안드로이드 8 이상 · 런처가 지원할 때).
  static Future<bool?> requestPin() => _call<bool>('requestPin');

  /// 위젯이 그릴 내용(JSON 글자)을 적고 다시 그린다.
  static Future<bool?> save(String json) =>
      _call<bool>('save', <String, Object?>{'json': json});

  /// 위젯 내용을 비운다(로그아웃 등). json을 주면 그 내용으로(예: 로그인 안내).
  static Future<bool?> clear([String? json]) =>
      _call<bool>('clear', <String, Object?>{'json': json});

  /// 마지막으로 적은 내용(JSON 글자).
  static Future<String?> lastFeed() => _call<String>('lastFeed');

  /// 위젯 ＋로 앱이 열렸으면 'add'(한 번 가져가면 지워짐).
  static Future<String?> takeAction() => _call<String>('takeAction');

  /// 앱이 켜져 있을 때 위젯 ＋를 누르면 알려 준다(그 뒤 takeAction으로 가져간다).
  static Stream<String> get actions {
    _listen();
    return _actions.stream;
  }

  /// 위젯 체크 · 15분 확인 · ＋ 작은 창 넣기 때 앱 화면 없이 부를 함수를 넘긴다.
  /// handler는 맨 바깥(top-level) 함수여야 하고 `@pragma('vm:entry-point')`를 붙인다.
  /// handler(action, id):
  ///   'done' · 'undo'(id = 할 일) · 'refresh'(15분마다 · id 없음)
  ///   'add'(id = 작은 창에 적은 글) · 'undoAdd'(id = 넣은 할 일들 · 쉼표로)
  /// 글자(JSON)를 돌려주면 작은 창이 받는다(예: {"ok":true,"items":[{"id","title","label"}]}).
  /// 예전처럼 아무것도 돌려주지 않는 함수(Future<void>)도 그대로 된다.
  static Future<bool?> setBackgroundHandler(
      Future<Object?> Function(String action, String id) handler) async {
    if (!isSupported) return null;
    final d = PluginUtilities.getCallbackHandle(oneoneQWidgetDispatcher);
    final c = PluginUtilities.getCallbackHandle(handler);
    if (d == null || c == null) return false;
    return _call<bool>('setHandles', <String, Object?>{
      'dispatcher': d.toRawHandle(),
      'callback': c.toRawHandle(),
    });
  }

  static void _listen() {
    if (_listening || !isSupported) return;
    _listening = true;
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'action') {
        _actions.add('${call.arguments ?? ''}');
      }
      return null;
    });
  }

  static Future<T?> _call<T>(String method, [Object? arguments]) async {
    if (!isSupported) return null;
    try {
      return await _channel.invokeMethod<T>(method, arguments);
    } catch (_) {
      return null;
    }
  }
}

/// 앱 화면 없이 도는 엔진의 첫 함수(안드로이드 BgRunner가 부름).
/// 앱이 setBackgroundHandler로 넘긴 함수를 찾아 (action, id)로 부른다.
@pragma('vm:entry-point')
void oneoneQWidgetDispatcher() {
  WidgetsFlutterBinding.ensureInitialized();
  DartPluginRegistrant.ensureInitialized();
  const ch = MethodChannel('oneone_q_widget/bg');
  ch.setMethodCallHandler((call) async {
    if (call.method != 'run') return null;
    final a = Map<Object?, Object?>.from(call.arguments as Map);
    final raw = a['handle'];
    final handle = raw is int ? raw : int.tryParse('$raw') ?? 0;
    final fn = PluginUtilities.getCallbackFromHandle(
        CallbackHandle.fromRawHandle(handle));
    if (fn == null) return false;
    try {
      final r = Function.apply(fn, <Object?>['${a['action'] ?? ''}', '${a['id'] ?? ''}']);
      final v = r is Future ? await r : r;
      // 작은 창 결과(JSON 글자)는 그대로 넘긴다
      return v is String ? v : true;
    } catch (_) {
      return false;
    }
  });
  ch.invokeMethod<void>('ready');
}
