import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// 11Q 홈 화면 위젯(안드로이드 · 갤럭시).
///
/// 아이폰 · 웹(FlutterFlow 테스트 모드)에서는 아무것도 하지 않고 null을 돌려준다.
/// 그래서 FlutterFlow 커스텀 코드에서 플랫폼을 따로 가리지 않고 불러도 된다.
class OneoneQWidget {
  OneoneQWidget._();

  static const MethodChannel _channel = MethodChannel('oneone_q_widget');

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

  static Future<T?> _call<T>(String method, [Object? arguments]) async {
    if (!isSupported) return null;
    try {
      return await _channel.invokeMethod<T>(method, arguments);
    } catch (_) {
      return null;
    }
  }
}
