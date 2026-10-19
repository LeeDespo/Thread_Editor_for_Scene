# Thread Editor 发布手册

本文件是 Thread Editor for Scene **Android APK 发布流程的唯一权威文档**，面向发布负责人。
README 面向使用者和贡献者，提供功能、环境以及 debug/release 构建与签名配置的快速说明；两者内容重叠时，发布流程（版本号规则、签名材料管理、tag、GitHub Release 与发布 CI）以本文为准，构建与环境值以 `androidApp/build.gradle.kts` 及本文第 1.3 节为准。

- 仓库：`LeeDespo/Thread_Editor_for_Scene`
- 包名：`com.scenepreset.editor`
- 发布资产：`ThreadEditor-Scene-v<version>.apk`
- 发布说明：GitHub Releases 页面上的 release notes

> 安全底线：本文不包含任何真实密码、keystore 内容或私钥。文中出现的证书指纹是 APK 中公开可见的证书信息，不是私钥。

---

## 1. 前置条件

### 1.1 干净的工作区与同步的分支

发布只能从干净、已推送的 `main` 分支进行。

```bash
# 在仓库根目录执行
git status --short          # 必须没有任何输出
git fetch origin
git switch main
git pull --ff-only
git rev-parse HEAD
git rev-parse origin/main   # 必须与上一行相同
```

- 工作区有未提交改动时不要发布；无法判断某个改动是否该进入发布时，先提交或还原。
- 不要在落后于 `origin/main` 的本地分支上打 tag。
- 发布提交必须先推送到 `origin/main`。`build.yml` 已在真实 Actions 上验证通过后，等待本次 `build.yml` 绿色再打 tag；首次启用 CI、尚未验证 `build.yml` 时，不要等待不存在的运行结果，按 6.3 手动发布，或先按 7.4 完成 `build.yml` 的首次验证。

### 1.2 签名材料

签名材料只放在本地仓库根目录，**永不提交**：

```text
ThreadEditor/
├── keystore.properties          # 本地签名配置，已被 .gitignore 忽略
└── threadeditor-release.jks     # 本地签名密钥库，已被 .gitignore 忽略
```

`keystore.properties` 必须且只能包含以下四个键：

| 键名 | 含义 | 本地值 |
| --- | --- | --- |
| `storeFile` | keystore 路径，相对仓库根目录 | `threadeditor-release.jks` |
| `storePassword` | keystore 密码 | `<仅本地保存>` |
| `keyAlias` | keystore 中的密钥别名 | `<与 jks 条目一致>` |
| `keyPassword` | 该密钥的密码 | `<仅本地保存>` |

现有 keystore 默认由仓库所有者（当前为 `LeeDespo`）或其指定的发布负责人离线保管。新的发布负责人应通过团队约定的安全渠道（例如面对面或团队密码管理器）向保管人取得 `threadeditor-release.jks` 和密码，不要在 issue、PR、公开聊天或邮件正文中传输密钥材料。若团队尚未指定保管人，先由仓库所有者指定，并完成离线备份交接。

确认两个文件没有被 Git 跟踪：

```bash
git check-ignore -v keystore.properties threadeditor-release.jks
# 期望分别命中 .gitignore 中的 keystore.properties 与 *.jks

git ls-files keystore.properties threadeditor-release.jks
# 必须没有任何输出
```

`.gitignore` 第 22–26 行的规则已经覆盖：

```text
# Signing (never commit)
*.jks
*.keystore
keystore.properties
credentials/
```

请把 keystore 和密码另外做离线备份。**丢失 keystore 后，已安装用户无法覆盖升级**，只能卸载重装。

#### 生成新 keystore（仅首次建立签名身份或换钥时）

已有可用 keystore 时不要重新生成；生成新钥会导致已安装用户无法覆盖升级。确认需要换钥后执行：

```bash
keytool -genkeypair \
  -keystore threadeditor-release.jks \
  -storetype PKCS12 \
  -alias threadeditor \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10950 \
  -dname "CN=Thread Editor for Scene, OU=Thread Editor, O=Thread Editor, C=CN"
```

该命令会交互式询问 keystore 密码，不要把密码写在命令行参数里。PKCS12 keystore 的密钥密码与 keystore 密码相同，keytool 可能忽略单独的密钥密码设置；因此 `keystore.properties` 中的 `storePassword` 与 `keyPassword` 应填写同一个密码。生成后用编辑器创建 `keystore.properties`，填入上表的四个键：`storeFile` 保持 `threadeditor-release.jks`，`keyAlias` 与 `-alias` 一致。随后重新执行上面的 `git check-ignore` / `git ls-files` 检查，并做离线备份。

### 1.3 构建与验证工具

- JDK 21（项目使用 `jvmToolchain(21)`）。
- Android SDK：`compileSdk` 37.0、`buildToolsVersion` 37.0.0、`minSdk` 24。Gradle 中写为 `compileSdk { version = release(37) { minorApiLevel = 0 } }`；README 的 `compileSdk 37` 是同一 API level 的简写。
- `ANDROID_HOME` 指向 SDK，或在仓库根目录提供被忽略的 `local.properties`（`sdk.dir=...`）。
- `adb` 可用，并连接一台已 root、已安装 Scene 的真机。
- `gh` 已登录且有仓库写权限：`gh auth status`。

macOS 上如果 `java` 不在 `PATH`，可临时使用 Android Studio 自带的 JBR：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

---

## 2. 版本号

版本号的唯一来源是：

```text
androidApp/build.gradle.kts:21-22
```

```kotlin
versionCode = 1
versionName = "1.0.0"
```

> 上面的数值是本文编写时的当前值；发布时以文件实际内容为准。

规则：

1. `versionCode` 必须是递增整数，每次发布至少 +1。
2. `versionName` 使用 `MAJOR.MINOR.PATCH`，tag 固定为 `v<versionName>`。
3. **两者必须在同一个提交里一起修改**，不能只改其中一个。
4. 不要在 `AndroidManifest.xml`、资源文件或代码里另设版本号；仓库中没有第二个版本来源。
5. tag 必须指向包含签名配置逻辑的提交。`v1.0.0` 的树早于签名配置的加入，因此不能用来验证 `release.yml` 的签名发布流程；新版本请基于当前 `main` 打 tag。

发布前核对：

```bash
grep -n -E 'versionCode|versionName' androidApp/build.gradle.kts
```

---

## 3. 签名

签名逻辑位于 `androidApp/build.gradle.kts:24-45`：构建时读取仓库根目录的 `keystore.properties`，只有属性非空才创建名为 `release` 的 `signingConfig` 并挂到 release buildType。

> 重要：`keystore.properties` 不存在或为空时，`assembleRelease` **不会报错**，而是静默产出未签名 APK。因此每次发布都必须执行第 5 节的 `apksigner verify`，不能只看 Gradle 是否成功。

### 3.1 本地签名检查

```bash
# 四个键名应齐全
cut -d= -f1 keystore.properties | sort

# 确认 keyAlias 与 jks 中的条目一致（按提示输入密码，不要把密码写在命令行）
keytool -list -keystore threadeditor-release.jks
```

不要在终端、脚本、CI 日志或 issue 中回显 `storePassword` / `keyPassword`。

### 3.2 签名材料泄漏时的处置

按“先止损、再换钥、最后清理历史”的顺序处理：

1. **立即停止发布**：删除公开的 release/tag，或至少删除 release 中的 APK 资产/把 release 转为 draft。删除资产可执行 `gh release delete-asset "v${VERSION}" "ThreadEditor-Scene-v${VERSION}.apk" --yes`；删除 release/tag 见 9.3、9.4。仅标记为 pre-release **不会**阻止下载，不能作为止损手段。
2. 如果 `.jks` 或 `keystore.properties` 只是被 `git add`、尚未提交：
   ```bash
   git rm --cached keystore.properties threadeditor-release.jks
   git status --short
   ```
3. 如果已经提交或推送到任何远端，视为密钥已泄漏：
   - 按 1.2 的 `keytool -genkeypair` 命令生成新的 keystore，更新本地 `keystore.properties`；
   - 更新 GitHub Actions 中的全部签名 secrets；
   - 用新密钥发布更高 `versionCode` 的版本。
4. 只从 Git 历史中删除文件**不能**让泄漏的密钥重新安全；删除历史只是清理，不是补救。
5. 由于 Android 不允许签名不同的 APK 覆盖安装，换钥后必须通知用户卸载旧版再安装新版。
6. 如果只是密码泄漏而 keystore 从未离开本机，也应更换 keystore 密码和 CI secrets；无法确认时按密钥泄漏处理。
7. 检查 GitHub 的 secret scanning / 安全告警，撤销任何被暴露的 token。

---

## 4. 构建 release APK

在仓库根目录执行：

```bash
./gradlew :androidApp:assembleRelease
```

产物：

```text
androidApp/build/outputs/apk/release/androidApp-release.apk
```

整理为发布资产：

```bash
VERSION="$(sed -n 's/^[[:space:]]*versionName = "\(.*\)"/\1/p' androidApp/build.gradle.kts)"
test -n "$VERSION"

mkdir -p dist
cp androidApp/build/outputs/apk/release/androidApp-release.apk \
  "dist/ThreadEditor-Scene-v${VERSION}.apk"
```

- `*.apk` 已被 `.gitignore` 忽略；`dist/` 中的文件不要提交。
- 建议在最终发布前执行一次干净构建：`./gradlew clean :androidApp:assembleRelease`。
- 如果构建时报找不到 SDK，检查 `ANDROID_HOME` 或 `local.properties`；`local.properties` 本身也已被忽略。

> 变量提醒：`VERSION` 只在上面的 shell 会话中有效。分节执行、重开终端或切换 shell 后，先重新运行 `VERSION=...` 和 `test -n "$VERSION"` 两行；第 5、6 节都依赖它。执行第 6 节的 tag/push 前，再运行一次 `test -n "$VERSION"` 确认非空。

> README 的「构建 release APK」是面向贡献者的快速说明；发布时以本节和 `androidApp/build.gradle.kts` 的实际配置为准。

---

## 5. 发布前验证

以下四项全部通过才能发布。

### 5.1 版本元数据

```bash
test -n "$VERSION"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
"$SDK/build-tools/37.0.0/aapt2" dump badging \
  "dist/ThreadEditor-Scene-v${VERSION}.apk" | head -3
```

期望输出包含：

```text
package: name='com.scenepreset.editor' versionCode='<递增整数>' versionName='<version>'
minSdkVersion:'24'
targetSdkVersion:'37'
```

`versionName` 必须与 tag、资产名和发布说明一致；`versionCode` 必须大于上一版。

### 5.2 签名

```bash
"$SDK/build-tools/37.0.0/apksigner" verify --print-certs \
  "dist/ThreadEditor-Scene-v${VERSION}.apk"
```

要求：

- 命令退出码为 0。
- 输出包含 `V2 Signer` 证书信息，且证书 DN / SHA-256 与发布证书一致。
- 当前 v1.0.0 的公开证书基线：
  - DN：`CN=Thread Editor for Scene, OU=Thread Editor, O=Thread Editor, C=CN`
  - SHA-256：`c25c3e8f65467e16b0ca6e3ed7f571b08c55a25fe4076cc0f5b4cfaf7d3e1f4c`
  - 签名方案：V2（`minSdk 24` 下有效且足够）
- 如果输出的证书不是上面这张，先停下来确认是否主动更换了签名密钥；更换密钥必须按 3.2 节处理。
- 如果提示未签名或 `DOES NOT VERIFY`，不要发布，检查 `keystore.properties` 的四个键和 `storeFile` 路径。

### 5.3 真机安装与手测（必须）

模拟器不能替代真机：应用通过 `su -mm` 读写 Scene 的配置目录，必须使用已 root、已安装 Scene 的真实设备。

```bash
adb devices
adb install -r "dist/ThreadEditor-Scene-v${VERSION}.apk"
adb shell dumpsys package com.scenepreset.editor | grep -E 'versionCode|versionName'
```

安装后至少验证：

- 应用能启动，版本号与预期一致。
- 能读取当前 Scene 配置；修改规则/类目后能保存，历史记录可回溯。
- JSON 导入/导出正常。
- Scene 8 / 9 / N1 版本识别与转换正常。
- 跨设备架构适配、核心拓扑相关页面正常。
- 覆盖安装上一版（同一签名）后数据仍在；再至少做一次全新安装验证。

### 5.4 校验和

```bash
shasum -a 256 "dist/ThreadEditor-Scene-v${VERSION}.apk"
# Linux 上可用 sha256sum 代替 shasum -a 256
```

把结果写入发布说明，方便用户核对下载文件。

---

## 6. 发布

### 6.1 提交并推送版本号

```bash
test -n "$VERSION"
git add androidApp/build.gradle.kts
git commit -m "Release v${VERSION}"
git push origin main
```

如果 `build.yml` 已经在真实 Actions 上验证通过，等待本次运行绿色后再打 tag。首次启用、`build.yml` 还没有任何真实运行结果时，不要等待不存在的运行：按 7.4 先完成 `build.yml` 的首次验证，或直接按 6.3 手动发布。

### 6.2 打 tag

```bash
test -n "$VERSION"
git tag -a "v${VERSION}" -m "Thread Editor for Scene v${VERSION}"
```

tag 必须是 annotated tag，名称为 `v<versionName>`。**先不要推送 tag**，先决定使用 6.3 的手动发布还是 6.4 的 CI 自动发布：

- 一旦推送 tag，仓库中的 `release.yml` 会自动触发并创建 GitHub Release。
- 当前建议先按 6.3 手动发布，直到 CI 在真实 GitHub Actions 上验证通过。

### 6.3 方式 A：手动发布（CI 验证通过前推荐）

如果 `release.yml` 已启用，先在 GitHub Actions 页面禁用它（`Release` 工作流 → `...` → `Disable workflow`），再推送 tag，避免自动创建 Release。发布完成后可重新启用。

```bash
test -n "$VERSION"
git push origin "v${VERSION}"

# 按 6.5 的模板创建发布说明；没有设置 $EDITOR 时使用 vi
mkdir -p dist
cat > dist/release-notes.md <<'EOF'
Thread Editor v<version>。

<按 6.5 模板补全本版本的发布说明>
EOF
${EDITOR:-vi} dist/release-notes.md

gh release create "v${VERSION}" \
  "dist/ThreadEditor-Scene-v${VERSION}.apk" \
  --title "v${VERSION} · <版本标题>" \
  --notes-file dist/release-notes.md
```

- `release-notes.md` 按 6.5 的模板创建，放在 `dist/` 下，不要提交；上面的命令会先写入一个占位文件，再打开编辑器补全。
- 不要加 `--draft` 后再忘记发布；确认 release 状态为 published。
- 发布说明中的资产名必须是 `ThreadEditor-Scene-v<version>.apk`。

### 6.4 方式 B：CI 自动发布

前提：

- 7.3 的四个 secrets 已配置；
- `release.yml` 已启用；
- tag 指向的提交包含签名配置；
- tag 名与 `versionName` 一致。

然后只需：

```bash
test -n "$VERSION"
git push origin "v${VERSION}"
```

`release.yml` 会依次构建、验证签名、重命名资产并执行 `gh release create`。**不要再手动执行 `gh release create`**，否则会因 release 已存在而失败。

CI 会在发布说明中写入包名、SHA-256 与安装提示；需要完整的中文说明时，先在本地按 6.3 中的 `mkdir` / `cat` / `${EDITOR:-vi}` 步骤创建 `dist/release-notes.md`（内容用 6.5 模板），再覆盖：

```bash
gh release edit "v${VERSION}" --notes-file dist/release-notes.md
```

### 6.5 发布说明模板

参考 v1.0.0 的写法，按版本替换尖括号内容：

````markdown
Thread Editor v<version>。

<一句话说明这个版本的主题。>

这个版本里做了这些事：

- **<变更 1>**：<说明>
- **<变更 2>**：<说明>
- **<变更 3>**：<说明>

**兼容性 / 升级**：<是否需要卸载重装、是否有配置格式变化、最低 Android 版本等>

**安装**：从下方 Assets 下载 `ThreadEditor-Scene-v<version>.apk` 安装即可。包以发布密钥签名（V2 scheme），覆盖安装旧版本请使用同一签名包；系统可能提示未知来源，允许一次即可。

**校验**：
- SHA-256：`<apk sha256>`
- 包名：`com.scenepreset.editor`
- versionCode / versionName：`<versionCode>` / `<versionName>`

需要 root 读写 Scene 的配置目录。许可与构建说明见 README（Kotlin 源文件为 GPL-3.0-or-later；Gradle 构建脚本文件头为 Apache-2.0）。欢迎 issue 和 PR。
````

---

## 7. CI

仓库包含两个 GitHub Actions 工作流：

| 文件 | 触发 | 作用 |
| --- | --- | --- |
| `.github/workflows/build.yml` | push 到 `main`、pull request | 编译 `:androidApp:assembleDebug`，上传 debug APK artifact |
| `.github/workflows/release.yml` | push tag `v*` | 注入签名材料，构建并验证 release APK，创建 GitHub Release 并上传资产 |

> **状态说明：`build.yml` 已于 2026-10-19 在真实 GitHub Actions 上运行通过（`main@4d6a6cb`，debug APK artifact 已上传）；`release.yml` 尚未在真实 tag 上运行过。** 首次发布仍建议按 6.3 手动流程；在 `release.yml` 首次成功之前，不要把它作为唯一发布方式。1.1/6.1 的“等待 build.yml”现在可以作为发布前置检查。

### 7.1 build.yml

- Runner：`ubuntu-latest`
- JDK：Temurin 21（`actions/setup-java@v4`）
- Android SDK：使用 runner 预装的 SDK（`$ANDROID_HOME`，缺省 `/usr/local/lib/android/sdk`），
  从其 `cmdline-tools` 中定位 `sdkmanager`，接受许可后安装所需包：
  ```bash
  "$SDKMANAGER" --install "platforms;android-37.0" "build-tools;37.0.0"
  ```
  注意包 ID 是 **`platforms;android-37.0`**，不是 `platforms;android-37`；后者在 SDK 仓库中不存在。
  不要改用 `android-actions/setup-android@v3`：它在 runner 的新版 cmdline-tools 上会尝试安装已不存在的
  `tools` 包并直接失败（2026-10-19 首次运行的实际失败原因）。
- Gradle：`gradle/actions/setup-gradle@v4`
- 构建：`./gradlew :androidApp:assembleDebug`
- 上传：`androidApp/build/outputs/apk/debug/androidApp-debug.apk`，artifact 名为 `ThreadEditor-debug`

`build.yml` 不需要任何 secret。

### 7.2 release.yml

- 触发：push tag `v*`
- 权限：`contents: write`
- Runner / JDK / SDK / Gradle 与 `build.yml` 相同
- 步骤：
  1. 校验 tag 与 `androidApp/build.gradle.kts` 中的 `versionName` 一致；
  2. 从 `KEYSTORE_BASE64` 还原 `threadeditor-release.jks` 到仓库根目录；
  3. 从 secrets 生成仓库根目录的 `keystore.properties`；
  4. 执行 `./gradlew :androidApp:assembleRelease`；
  5. 用 `apksigner verify --print-certs` 验证签名（缺失签名会在这里失败）；
  6. 重命名为 `dist/ThreadEditor-Scene-<tag>.apk`；
  7. 无论成功失败，删除 runner 上的 jks 与 `keystore.properties`（该步骤在创建 Release 之前执行）；
  8. 用 `gh release create` 创建 release，说明中包含包名与 SHA-256，并上传资产（仅当前面步骤成功时执行）。

工作流不会打印密码或 base64 内容；签名相关日志只出现文件名、版本号和证书信息。

### 7.3 需要的 secrets

如果仓库的 Actions 尚未启用，先在 **Settings → Actions → General** 中允许 Actions。

在 GitHub 仓库的 **Settings → Secrets and variables → Actions → New repository secret** 中添加以下四个 **Repository secrets**（引用时不区分大小写，GitHub 会统一以大写保存；本文与工作流仍保持下列写法）：

| Secret 名称 | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | `threadeditor-release.jks` 的 Base64 编码 |
| `KEYSTORE_STORE_PASSWORD` | `keystore.properties` 中的 `storePassword` |
| `KEYSTORE_KEY_ALIAS` | `keystore.properties` 中的 `keyAlias` |
| `KEYSTORE_KEY_PASSWORD` | `keystore.properties` 中的 `keyPassword` |

用 `gh` 配置（推荐从标准输入读取，避免密码进入 shell 历史）：

```bash
# jks 的 Base64（macOS/Linux 均可；换行会被 base64 解码器忽略）
base64 < threadeditor-release.jks | gh secret set KEYSTORE_BASE64

# 下面三条按提示输入，不要把真实值写在命令行参数里
gh secret set KEYSTORE_STORE_PASSWORD
gh secret set KEYSTORE_KEY_ALIAS
gh secret set KEYSTORE_KEY_PASSWORD

# 只查看名称，不查看值
gh secret list
```

- `KEYSTORE_KEY_ALIAS` 不是敏感信息，但为了让工作流只依赖一套 secret，仍按 secret 处理。
- 密码建议使用字母、数字和常见符号，避免反斜杠；`keystore.properties` 中反斜杠是转义字符。
- 更换 keystore 或密码后，必须同步更新这四个 secrets。
- 不要把 secret 写进 workflow 文件、README、issue 或提交历史。

### 7.4 首次启用的验证顺序

1. 提交这两个 workflow，开一个 PR，确认 `build.yml` 被触发且通过。
2. 合并到 `main`，确认 push 触发的 `build.yml` 通过，并下载 artifact 检查 APK。
3. 在下一个正式版本上验证 `release.yml`。不要用假 tag 污染主仓库；如果必须预演，在 fork 或测试仓库进行。
4. 如果 `release.yml` 失败，按第 8、9 节检查并清理已产生的 release/tag。

---

## 8. 发布后检查

```bash
VERSION="$(sed -n 's/^[[:space:]]*versionName = "\(.*\)"/\1/p' androidApp/build.gradle.kts)"
test -n "$VERSION"

gh release view "v${VERSION}" \
  --json tagName,name,isDraft,isPrerelease,url,assets

mkdir -p /tmp/threadeditor-release
gh release download "v${VERSION}" \
  --pattern "ThreadEditor-Scene-v${VERSION}.apk" \
  --dir /tmp/threadeditor-release \
  --clobber

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
shasum -a 256 "/tmp/threadeditor-release/ThreadEditor-Scene-v${VERSION}.apk"
"$SDK/build-tools/37.0.0/apksigner" verify --print-certs \
  "/tmp/threadeditor-release/ThreadEditor-Scene-v${VERSION}.apk"
"$SDK/build-tools/37.0.0/aapt2" dump badging \
  "/tmp/threadeditor-release/ThreadEditor-Scene-v${VERSION}.apk" | head -3
```

逐项确认：

- Release 不是 draft，也不是误标的 pre-release。
- tag、release 标题、说明中的版本号一致。
- 资产名是 `ThreadEditor-Scene-v<version>.apk`，大小非 0，状态为 uploaded。
- 下载后的 SHA-256 与发布说明中的 SHA-256 一致（CI 会自动写入；手动发布时按 6.5 的模板写入）。
- `apksigner` 验证通过，证书与发布证书一致。
- `aapt2` 读出的 `versionCode` / `versionName` 与预期一致。
- Release 页面可以直接下载资产。

---

## 9. 补救

### 9.1 只改发布说明

按 6.3 的创建步骤和 6.5 的模板准备好本地 `dist/release-notes.md` 后执行：

```bash
gh release edit "v${VERSION}" --notes-file dist/release-notes.md
```

### 9.2 替换资产

```bash
gh release upload "v${VERSION}" \
  "dist/ThreadEditor-Scene-v${VERSION}.apk" --clobber
```

替换后重新执行第 8 节的全部检查。

### 9.3 删除 Release（保留 tag）

```bash
gh release delete "v${VERSION}" --yes
```

### 9.4 删除 Release 和远端 tag

```bash
gh release delete "v${VERSION}" --yes --cleanup-tag
git tag -d "v${VERSION}"
```

如果 `--cleanup-tag` 不可用或需要单独删除远端 tag：

```bash
git push origin ":refs/tags/v${VERSION}"
git tag -d "v${VERSION}"
```

### 9.5 tag 指向错误提交

1. 先删除对应 Release，避免用户继续下载。
2. 删除远端和本地 tag（9.4）。
3. 修正提交后重新打 tag 并推送。
4. 重新发布，并确认 `versionCode` 已递增。

### 9.6 已经发布了错误的 APK

- 如果只是说明写错，用 9.1 修正。
- 如果 APK 内容错误但签名正确：发布一个更高 `versionCode` 的修复版本；同一签名可以直接覆盖升级。不要覆盖同一个 tag 的资产后假装版本没变。
- 如果 APK 未签名或签名错误：立即删除 Release/tag，修复后重新发布。
- 如果签名密钥泄漏：按 3.2 节换钥，并明确告知用户需要卸载重装。

---

## 10. 禁止事项

- 禁止提交 `threadeditor-release.jks`、`keystore.properties` 或任何密码、私钥。
- 禁止在命令、CI 日志、issue、PR 中回显密码。
- 禁止发布未签名的 APK：Gradle 成功不代表签名成功，必须运行 `apksigner verify`。
- 禁止在版本号提交之前打 tag。
- 禁止复用已发布的 tag；需要修正时先删除 Release 和 tag，再重新发布。
- 禁止在真机验证之前宣布发布完成。
