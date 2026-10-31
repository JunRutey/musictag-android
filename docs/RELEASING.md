# 构建与发布流程

项目有两套构建：**调试验证包**（日常改动用来验证）和**发行包**（确认后才打 tag）。两者包名不同，
可以同时装在一台手机上。

## 调试验证包（默认）

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

| 项 | 值 |
|---|---|
| 包名 | `com.xjcheng.musictageditor.debug`（对应 `applicationIdSuffix = ".debug"`） |
| 版本名 | `x.y.z-debug`（对应 `versionNameSuffix = "-debug"`） |
| 签名 | Android 调试密钥（`~/.android/debug.keystore`） |
| 用途 | 自测、给你确认；**不发布**，不进发行页 |

因为包名不同，它不会覆盖已安装的发行版，两者并存互不影响；`FileProvider` 的 authority 由
`${applicationId}` 派生，也会自动变成 `.debug.provider`。

CI 每次推送都会构建 `app-debug` 并在 Actions 里作为 artifact 提供（`Build & test` 任务的
`app-debug`），可以直接下载安装。

## 发行包（确认之后）

```bash
# 1) 确认版本号与 CHANGELOG
#    app/build.gradle.kts 的 versionName 默认值 + CHANGELOG.md 对应小节
# 2) 打 tag 并推送，Release 工作流会签名、校验、发布
git tag -a v1.1.10 -m "Music Tag 1.1.10"
git push origin v1.1.10
```

发行版由 `.github/workflows/release.yml` 用仓库密钥签名，并做三件事：

1. `apksigner verify` 校验签名；
2. `tools/manifest_contract.py` 用**发行包**校验兼容契约（包名 `com.xjcheng.musictageditor`、
   组件名与 intent-filter 不变）；
3. 从 `CHANGELOG.md` 抽取该版本小节，生成**中文发行说明**（安装说明 + 更新内容 + 文件摘要）。

## 兼容契约校验对象

* `Build & test` 里的契约与 intent 矩阵校验：`app-release-unsigned.apk`，因为契约描述的是
  发行身份；调试包带 `.debug` 后缀，天然不满足包名一项。
* `release.yml` 里的校验：签名后的发布包本身。

## 发行版清理记录

2026-10-31：删除 `v1.1.0` 至 `v1.1.8` 的发行页与标签，只保留当时验证通过的 `v1.1.9`。
原因：早期版本把未完成/不可用的功能打进了发行包（例如 1.1.7 因偏好类型变更在启动时闪退），
让下载页同时存在可用的和不可用的包容易误导。

## 常用命令

```bash
./gradlew :app:assembleDebug            # 调试验证包
./gradlew :app:assembleRelease -Pmusictag.unsignedRelease=true   # 未签名发行包（CI 用）
./gradlew test                          # 单元测试
python3 tools/selfcheck.py              # 本地自检（资源、契约文件、JNI 配对、硬编码文案）
./gradlew :app:lintDebug                # Lint（CI 会跑）
```
