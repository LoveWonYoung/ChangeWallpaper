# Android 自动换壁纸客户端

用 Kotlin 开发一款 Android App：定时从自建服务拉取壁纸，并设为系统壁纸。

服务地址：

```text
https://wallpaper.wonyoung.top
```

网页图库（可对照联调）：

```text
https://wallpaper.wonyoung.top/
```

本文可直接交给 Cursor / Android Studio 作为实现说明。

---

## 产品目标

第一版只做这些：

1. 后台定时拉取壁纸并自动设置
2. 手动刷新
3. 可选：只换桌面、只换锁屏、或两者都换
4. Wi-Fi 和移动数据均可下载
5. 可选：预览图库，点击设为壁纸

不做账号、分类、上传、多服务器切换。

---

## 推荐工作模式

服务端有两类接口，含义不同，**不要混用**。

| 模式 | 接口 | 适用 | 说明 |
|------|------|------|------|
| **同步模式（推荐默认）** | `GET /current` | 多台手机要显示同一张 | 按墙上时钟每 15 分钟换一张。所有设备同一时刻拿到同一张。服务重启也不会打乱当前窗口。 |
| 独立轮换 | `GET /next` | 只有一台设备 | 服务端洗牌队列，一轮内不重复。 |
| 随机 | `GET /random` | 调试 | 随机一张，且避免与「上一次全站请求」相同。 |

注意：

- `/next` 和 `/random` 的状态是**整站共享**的。两台手机同时打 `/next`，会拿到不同图，还会互相消耗队列。
- 多设备同步必须用 `/current`。
- `/current` 即使晚几分钟再请求，仍是当前 15 分钟窗口的那张图，所以 WorkManager 略有延迟也没关系。

默认实现：**每 5 分钟请求 `/current`，然后设壁纸。** 服务端仍按 15 分钟窗口切换，因此客户端最多约 5 分钟就能跟进新窗口。

---

## 服务端 API

Base URL：`https://wallpaper.wonyoung.top`

只使用 HTTPS。不要写死 IP，不要走 HTTP。

动态接口带了 `Cache-Control: no-store`。客户端 **禁止** 用 HTTP 缓存这几个地址：`/current`、`/next`、`/random`、`/info`、`/health`。

静态原图和缩略图可以缓存。

### GET /current

返回当前 15 分钟窗口的壁纸（原始图片字节）。

```text
GET /current
→ 200
Content-Type: image/jpeg   （也可能是 image/png、image/webp）
```

切换对齐 Unix 时间的 `:00` / `:15` / `:30` / `:45`（北京时间同样对齐）。

### GET /next

返回洗牌队列中的下一张。一轮内不重复，换轮时尽量不与上一轮最后一张相同。

### GET /random

随机一张。至少两张图时，不会与上一次全站 `/random` 相同。

### GET /health

```json
{"status":"ok"}
```

### GET /info

```json
{
  "wallpaper_count": 7,
  "supported_formats": ["jpg", "jpeg", "png", "webp"],
  "wallpapers": [
    "2C0B1182-4BD7-4ED5-9968-0F28E5FED013_1_105_c.jpeg"
  ]
}
```

`wallpapers` 是文件名列表，已排序。图库预览用这个列表。

### GET /thumb/{filename}

缩略图，JPEG。文件名需要 URL 编码。

```text
GET /thumb/xxx.jpeg
→ 200  Content-Type: image/jpeg
```

### GET /image/{filename}

原图。文件名必须是 `/info` 里出现过的名字，否则 404。禁止把用户输入直接拼进本地路径。

```text
GET /image/xxx.jpeg
→ 200  Content-Type: image/jpeg
```

### 错误码

| 状态 | 含义 |
|------|------|
| 200 | 成功，图片或 JSON |
| 404 | 没有壁纸，或文件名不存在，或未知路径 |
| 500 | 服务读目录失败 |

响应 body 是简短英文，例如 `no wallpapers`、`internal error`。不要依赖文案解析，用 HTTP 状态码。

支持格式：`.jpg` `.jpeg` `.png` `.webp`。Android 系统解码器可以直接处理。

---

## 应用架构

```text
UI（Compose）
  开关、间隔、桌面/锁屏、手动刷新、图库预览
        |
        v
Settings（DataStore）
        |
        v
WorkManager 链式 OneTimeWork
        |
        v
WallpaperWorker
        |
        |  OkHttp GET /current
        v
缓存到 app 私有目录
        |
        v
BitmapFactory / ImageDecoder
        |
        v
WallpaperManager.setBitmap(...)
```

模块建议：

- `data`：OkHttp、下载、DataStore
- `work`：`WallpaperWorker`
- `ui`：设置页 + 图库
- 不要上 Room、不要上后端账号

---

## 工程约定

- 语言：Kotlin
- UI：Jetpack Compose
- 最低 SDK：26（Android 8.0）
- target / compile：34 或 35
- 网络：OkHttp（或 Retrofit + OkHttp）
- 后台任务：WorkManager
- 设置：DataStore Preferences
- 图库加载：Coil

Gradle 依赖（按仓库最新稳定版即可）：

```kotlin
implementation("androidx.work:work-runtime-ktx:2.9.1")
implementation("androidx.datastore:datastore-preferences:1.1.1")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("io.coil-kt:coil-compose:2.7.0")
```

---

## 权限

`AndroidManifest.xml`：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.SET_WALLPAPER" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

说明：

- `SET_WALLPAPER` 是安装时权限，不需要运行时弹窗。
- 不要申请读写相册。图片下载到 `context.cacheDir` 即可。
- 不需要前台服务。用带延迟的单次 WorkManager 任务连续调度 5 分钟检查。
- Android 13+ 如果以后加通知，再申请 `POST_NOTIFICATIONS`。第一版可以不发通知。

---

## 设置项

用 DataStore 保存：

| key | 类型 | 默认 | 说明 |
|-----|------|------|------|
| `enabled` | Boolean | false | 自动换壁纸开关 |
| `mode` | String | `current` | `current` / `next` / `random` |
| `interval_minutes` | Int | 5 | 最小 5。使用连续单次任务调度 |
| `target` | String | `both` | `home` / `lock` / `both` |

同步模式默认每 5 分钟检查一次。更长也可以，只是跟进服务端新窗口会更慢。

---

## 后台任务

WorkManager 的周期任务最短为 15 分钟。要实现 5 分钟检查，使用带 `initialDelay` 的唯一单次任务；每次执行结束后安排下一次，并在停止时取消调度任务。网络约束使用 `NetworkType.CONNECTED`，允许 Wi-Fi 和移动数据。具体实现见 `WallpaperScheduler`。

```kotlin
fun makeWallpaperWork(intervalMinutes: Long) =
    OneTimeWorkRequestBuilder<WallpaperWorker>()
        .setInitialDelay(intervalMinutes.coerceAtLeast(5), TimeUnit.MINUTES)
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .build()
```

开关关掉时取消所有自动调度任务。改间隔或来源时重新安排；手动刷新使用独立的 `OneTimeWorkRequest`。

### Worker 流程

```kotlin
class WallpaperWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val bytes = download(endpointForMode())
            val bitmap = decodeBitmap(bytes) ?: return Result.retry()
            applyWallpaper(applicationContext, bitmap)
            bitmap.recycle()
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        }
    }
}
```

`endpointForMode()`：

- `current` → `https://wallpaper.wonyoung.top/current`
- `next` → `https://wallpaper.wonyoung.top/next`
- `random` → `https://wallpaper.wonyoung.top/random`

### 下载时禁止缓存

```kotlin
val request = Request.Builder()
    .url(url)
    .cacheControl(CacheControl.FORCE_NETWORK)
    .header("Cache-Control", "no-cache")
    .build()
```

不要把 `/current` 配进 OkHttp Cache。同一 URL 在 15 分钟后内容会变，缓存会导致一直显示旧图。

建议超时：连接 10s，读取 30s。

下载成功后写入：

```text
context.cacheDir / "wallpaper-latest"
```

再用 `BitmapFactory.decodeByteArray` 或 `ImageDecoder` 解码。

大图注意：

```kotlin
BitmapFactory.Options().apply {
    inJustDecodeBounds = true
    // 再按屏幕最长边计算 inSampleSize，避免 OOM
}
```

按屏幕宽高做 `inSampleSize`，不要原图硬设。当前服务端图片大约 1086×724，一般没问题，但要按以后可能更大的图来写。

---

## 设置系统壁纸

```kotlin
fun applyWallpaper(context: Context, bitmap: Bitmap, target: String = "both") {
    val wm = WallpaperManager.getInstance(context)
    val flags = when (target) {
        "home" -> WallpaperManager.FLAG_SYSTEM
        "lock" -> WallpaperManager.FLAG_LOCK
        else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
    }
    wm.setBitmap(bitmap, null, true, flags)
}
```

部分厂商对锁屏壁纸有限制。如果 `FLAG_LOCK` 失败，至少保证桌面设置成功，不要让整个 Worker 失败。

不要用 `setStream` 走公开 MediaStore。私有缓存文件即可。

---

## 界面（第一版）

一个设置页即可。

- 标题：自动壁纸
- 开关：启用自动更换
- 模式：同步（推荐）/ 独立轮换 / 随机
- 间隔：最短 5 分钟
- 范围：桌面、锁屏、两者
- 网络：Wi-Fi 和移动数据
- 按钮：立即更换
- 状态：上次成功时间、失败原因
- 次级入口：图库

图库页：

1. `GET /info` 取文件名列表
2. Coil 加载 `https://wallpaper.wonyoung.top/thumb/{URLEncoder.encode(name, "UTF-8")}`
3. 点击后加载 `/image/{name}` 全屏预览
4. 按钮「设为壁纸」走同一套 `applyWallpaper`

文件名含 UUID，必须 `URLEncoder.encode`，不要手拼。

---

## 同步模式的时间策略

`/current` 按 15 分钟整点切换。客户端两种做法都可以：

1. **当前实现**：每 5 分钟用链式 `OneTimeWorkRequest` 拉一次 `/current`，更快发现服务端窗口变化。
2. **更准时**：也可以算到下一个 `:00/:15/:30/:45` 的延迟，但需要处理设备时钟和系统延迟。

打开 App 时如果自动更换是开的，立刻拉一次 `/current`，避免用户要等 15 分钟才看到第一张。

---

## 错误处理

| 情况 | 行为 |
|------|------|
| 无网络 | Worker `retry`，界面提示稍后 |
| 404 | 不要疯狂重试。记失败「服务端没有壁纸」 |
| 500 | `retry`，指数退避 |
| 解码失败 | `retry` 一次，仍失败则记日志 |
| 飞行模式 | WorkManager 约束会推迟，恢复网络后执行 |

健康检查：设置页可请求 `/health`，用于「服务是否在线」。不是每次换壁纸都要先打 `/health`。

---

## 不要做的事

- 不要在客户端再洗牌、再自己实现「15 分钟换图」。同步模式以服务端 `/current` 为准。
- 不要缓存 `/current` `/next` `/random`。
- 不要把文件名当本地路径拼接。
- 不要请求未知路径或把用户输入传给服务器当路径。
- 不要用 `AlarmManager.setExactAndAllowWhileIdle` 做第一版，权限和电量都麻烦。
- 不要引入登录、Firebase、统计 SDK。

---

## 建议实现顺序

1. 空项目 + 权限 + 设置页 UI
2. OkHttp 下载 `/current`，手动按钮设壁纸
3. WorkManager 5 分钟链式任务 + 开关
4. 桌面 / 锁屏
5. 上次成功时间
6. 图库预览（`/info` + `/thumb` + `/image`）

---

## 联调

电脑上可先确认服务：

```bash
curl -I https://wallpaper.wonyoung.top/health
curl -I https://wallpaper.wonyoung.top/current
curl -s https://wallpaper.wonyoung.top/info
```

期望：

- `/health` → `200` JSON `{"status":"ok"}`
- `/current` → `200` `Content-Type: image/*`
- `/info` → `wallpaper_count` ≥ 1

Android 用 Logcat 打：

- 请求 URL
- HTTP 状态
- 字节数
- 设置壁纸是否成功

不要把完整文件名以外的敏感信息写进崩溃上报（当前也没有上报）。

---

## 验收清单

- [ ] 打开开关后，无需保持 App 在前台，壁纸会自动换
- [ ] 两台手机都开同步模式时，同一时段壁纸相同
- [ ] 点「立即更换」马上更新
- [ ] Wi-Fi 和移动数据下都能下载
- [ ] 关闭开关后不再自动更换
- [ ] 图库能列出全部壁纸，缩略图可点开大图
- [ ] 杀进程、重启手机后，周期任务仍在（WorkManager 会恢复）
- [ ] 飞行模式恢复后会补一次（允许系统延迟）
