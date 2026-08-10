# 自动壁纸（ChangeWallpaper）

一款使用 Kotlin 和 Jetpack Compose 开发的 Android 自动壁纸应用。选择一个图片文件夹后，应用会按照设定的时间间隔循环更换壁纸。

## 功能

- 使用 Android 系统文件夹选择器，无需申请整个存储空间的访问权限
- 按文件名顺序循环使用 JPG、PNG、WebP、HEIC 等图片
- 支持按分钟、小时或天设置切换间隔
- 支持更换主屏幕、锁定屏幕或两者
- 支持立即更换一次
- 记录最近一次更换结果和错误信息
- 使用 WorkManager 在后台可靠调度任务

## 环境要求

- Android 7.0（API 24）及以上
- Android Studio / JDK 11 及以上

## 构建

```bash
./gradlew :app:assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 使用说明

1. 点击“选择文件夹”，选择存放壁纸图片的目录。
2. 设置切换间隔和壁纸应用范围。
3. 开启“自动更换”。

Android 的可靠周期后台任务最短间隔为 15 分钟，实际执行时间可能受设备省电策略影响。
