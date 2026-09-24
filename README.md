# QTV

电视与手机通用的 IPTV / 家庭影音播放器（Java + Android View，无第三方 UI 库）。

- 内置 15 个公开频道分类（中文/多语言/少儿/纪录片/音乐/全球 Free TV 等），离线清单打包在 `app/src/main/assets/playlists/`，联网时自动从上游更新并缓存
- 支持导入 M3U 播放列表、播放本地视频、输入服务器直链（MP4 / M3U8）
- 收藏、最近播放、并行测速、清理失效源、全屏播放、大字模式
- 遥控器 / 触屏 / 鼠标 / USB 键盘均可操作（LEANBACK_LAUNCHER）

## 构建

需要 JDK 17 与 Android SDK（Platform 35）。首次构建前设置 `ANDROID_HOME` 环境变量，或在项目根创建 `local.properties`：

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

```powershell
./gradlew.bat :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（已 gitignore）。

## 说明

频道地址由第三方公开仓库维护，播放可用性与内容授权由相应服务提供方负责。本仓库代码仅作个人学习存档；商用发行请配置已获授权的频道清单。
