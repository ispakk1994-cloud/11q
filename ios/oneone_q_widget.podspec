# 11Q 홈 화면 위젯(아이폰) 통로 · 비밀 값(키 · 토큰 · 서버 주소)은 넣지 않음
Pod::Spec.new do |s|
  s.name             = 'oneone_q_widget'
  s.version          = '0.6.0'
  s.summary          = '11Q home screen widget bridge for iPhone.'
  s.description      = 'Saves the 11Q widget feed (JSON) to the app group, reloads the iPhone widgets and runs the app code headless for widget check, quick add and background refresh. No secrets.'
  s.homepage         = 'https://github.com/ispakk1994-cloud/11q'
  s.license          = { :type => 'Proprietary', :text => 'All rights reserved.' }
  s.author           = { '11Q' => 'noreply@example.com' }
  s.source           = { :path => '.' }
  s.source_files     = 'Classes/**/*.swift'
  s.resource_bundles = { 'oneone_q_widget_privacy' => ['Resources/PrivacyInfo.xcprivacy'] }
  s.dependency 'Flutter'
  s.platform         = :ios, '13.0'
  s.weak_frameworks  = 'WidgetKit'
  s.frameworks       = 'UserNotifications', 'BackgroundTasks'
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version    = '5.0'
end
