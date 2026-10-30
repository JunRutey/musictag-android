# 日志与导出

应用把自己发生的事写进**应用私有目录**的日志文件，同时镜像到系统 logcat。要排查问题，按下面
任意一种方式取日志即可。

## 日志在哪里

| 位置 | 说明 |
|---|---|
| `files/logs/app.log` | 主日志，超过 512 KiB 轮转 |
| `files/logs/app.log.1` | 上一份归档，只保留一份 |
| logcat tag | `MusicTag/Library`、`MusicTag/MusicTagEditor`、`MusicTag/LogExport`、`MusicTag/Crash`、`MusicTag/AppLog` |

私有目录在 `/data/data/com.xjcheng.musictageditor/files/logs/`，非 root 的 adb 读不到，
所以下面给的是「导出到公共目录」和「直接读 logcat」两条路。

## 方式一：设置里导出到「下载」（推荐，免电脑）

设置 → 关于 → **导出到「下载」**。成功后该行会显示保存位置，例如：

```
Download/musictag-log-20261031-034500.txt
```

然后任选其一取走文件：

```bash
adb pull /sdcard/Download/musictag-log-20261031-034500.txt
```

- 也可以直接用手机上的文件管理器打开「下载」目录，长按分享到微信/邮箱；
- Android 9 及以下若没有存储权限，会退回应用外部目录
  `Android/data/com.xjcheng.musictageditor/files/musictag-log-…txt`。

同一分组里还有：

- **导出日志**：走系统分享面板（部分设备没有可分享的应用，所以才有上面那条）；
- **清空日志**：删掉 `logs/` 下的所有文件；
- 摘要行显示当前日志大小。

## 方式二：直接读 logcat（免安装权限、免 root）

```bash
# 只看本应用的行
adb logcat -d | grep MusicTag

# Windows PowerShell
adb logcat -d | Select-String MusicTag

# 只看某个 tag
adb logcat -d -s "MusicTag/MusicTagEditor:*"

# 存成文件再发
adb logcat -d -s "MusicTag/*:*" > musictag-log.txt
```

实时跟踪（复现问题时用）：

```bash
adb logcat -s "MusicTag/*:*"
```

`-d` 表示导出当前缓冲区后退出；不加 `-d` 会持续输出。

## 崩溃

未捕获异常由 `AppLog` 的 handler 记录，级别 `E`，tag `Crash`，内容包含线程名与完整堆栈；
日志里搜 `Crash` 即可。记录发生在进程退出之前，所以「直接闪退」也会留下内容。

## 无线调试

Android 11+ 可以在开发者选项里用「无线调试 → 使用配对码配对」：

```bash
adb pair 手机IP:配对端口
adb connect 手机IP:调试端口
adb logcat -s "MusicTag/*:*"
```

## 代码位置

- `app/src/main/java/com/xjcheng/musictageditor/logging/AppLog.kt`：写文件、轮转、崩溃 handler
- `app/src/main/java/com/xjcheng/musictageditor/logging/LogExporter.kt`：导出到 Downloads
