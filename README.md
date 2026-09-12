# Thread Editor

一个基于 **Miuix**（Compose Multiplatform UI 库）的开源 Android 应用，用于编辑
[Scene](https://omarea.com/#/) 的性能调节配置文件：`threads.json`（核心分配）
与 `categories.json`（应用类目）。

**主要功能**

- 可视化编辑线程规则与应用类目，实时冲突检测
- Scene 8 / 9 / N1 三代配置互转，导入时按需版本转换
- 跨设备架构自适应转化（核心掩码按档位映射到本机核心簇）
- 自定义核心 / 核心簇拓扑，历史记录与一键回溯
- Monet 动态取色，MIUI / HyperOS 设计语言
- 导入 / 导出 JSON，卡片长按进入批量导出模式

> 需要 root 权限读写 Scene 配置目录。

界面遵循 MIUI / HyperOS 设计语言：莫奈（Monet）动态取色、Squircle 圆角、卡片滑入滑出、
大标题折叠、横向手势切页。应用同时支持 **Scene 8 / Scene 9 / Scene N1** 三种版本的
`threads.json` 与 `categories.json` 编辑，导入/回溯时按需做版本转换。

---

## 一、如何构建

### 环境要求

- **JDK 21**（`jvmToolchain(21)`）；Gradle 构建会自动使用工具链。
- **Android SDK**：`compileSdk` 37（`buildToolsVersion 37.0.0`），`minSdk 24`。
- **Android Gradle Plugin 9.4.0**、**Kotlin 2.4.10**、**JetBrains Compose Multiplatform 1.12.0**。
- 构建仓库已配置国内镜像（阿里云、腾讯云），国内网络可直接拉取依赖。

### 命令

```bash
# 在项目根目录 ThreadEditor/ 下执行
./gradlew :androidApp:assembleDebug
```

产物路径：

```
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

安装到已连接的设备：

```bash
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

> 项目是多模块：`:androidApp`（Android 入口 + root 相关实现）与 `:shared`
> （Compose Multiplatform 共享 UI 与数据/模型层）。

### 依赖组件

| 组件 | 版本 | 用途 |
| --- | --- | --- |
| Miuix (`miuix-ui`) | 0.9.4-rc01 | 核心 UI：TopAppBar/Scaffold/Card/TextField/TabRow 等 |
| Miuix (`miuix-icons`) | 0.9.4-rc01 | 扩展图标 |
| Miuix (`miuix-preference`) | 0.9.4-rc01 | “更多”页设置项（ArrowPreference / SwitchPreference） |
| Miuix (`miuix-nav`) | 0.9.4-rc01 | `NavDisplay` 导航（二级页覆盖滑入） |
| JetBrains Compose Multiplatform | 1.12.0 | Compose 运行时 |
| kotlinx.serialization.json | 1.11.0 | threads.json / categories.json / 历史记录 编解码 |
| androidx.activity:activity-compose | 1.13.0 | `setContent` 入口 |
| androidx.navigationevent:navigationevent-compose | 1.1.2 | （经 miuix-nav 传递使用） |
| AGP / Kotlin | 9.4.0 / 2.4.10 | 构建与编译器 |

> Miuix 内部通过 `materialkolor` 生成 Material You（TonalSpot / Neutral / Vibrant …）动态配色，
> 因此“莫奈取色”由 Miuix 的 `ThemeController` 提供。

---

## 二、组件与实现说明

### 顶层导航

- 一级页面（底部导航三栏）：**线程 / 类目 / 更多**，内容以横向卡片滑入滑出切换，底栏固定。
- 线程页内含 **默认（app_cpuset）/ 进阶（cpuset）** 分段，规则卡片滑入滑出切换。
- 二级页面（编辑规则、编辑类目、主题设置、历史、导入导出、自定义核心、关于）用 `NavDisplay` 全屏覆盖并滑入，
  覆盖含底栏。
- 线程 / 类目主页：右下角为**添加**按钮；规则改动后，添加按钮上方出现**保存**（写入配置）
  与**恢复**（撤销未保存修改）两个圆形按钮，尺寸与添加按钮一致。

### 线程编辑（`HomeScreen` + `EditScreen`）

- 线程页：搜索（规则名 / 包名 / 应用名 / 类目 / 类目导入的应用）、规则卡片（类目标签 + 应用图标，
  最多显示 10 个图标，超出接 “...”，再显示数量）、点击卡片进入编辑，卡片上的按钮为停用/恢复（停用置灰）、
  右下角新增；规则被改动后，新增按钮上方出现圆形“保存/恢复”（与添加按钮同尺寸）。
- 编辑规则页：基本信息（friendly / packages / categories）、默认↔进阶分段、按所选 Scene 版本
  显示对应字段、核心簇与核心选择、comm 映射（线程名在上、核心选择在下）、每种字段带 `ⓘ` 信息说明；保存前做冲突检查。
- 类目选择：通过“添加类目”弹窗，可手工输入类目 ID（逗号分隔），也可用开关从 `categories.json`
  列表导入；已在其它规则使用的类目会禁选；通配类目不可选（想用通配请在包名添加 `*`）。

### 类目编辑（`CategoriesScreen` + `CategoryEditScreen`）

- 类目页：搜索（类目名 / 包名 / 应用名 / 组件）、按类目分卡片、点击卡片进入编辑、停用/恢复按钮、脏状态保存/恢复。
- 编辑类目：friendly / category（ID）/ packages / activities，字段带 `ⓘ`，包名、组件冲突检测。

### 更多

- 主题设置：颜色模式（跟随系统 / 浅色 / 深色 / 莫奈·跟随系统 / 莫奈·浅色 / 莫奈·深色）、
  启用 Monet、强调色（预设色板）。
- Scene 版本：Scene 8 / 9 / N1，点击选择；每次启动检测已安装 Scene 版本并提示。
- Scene 配置管理：自定义配置目录（更改后立即重新加载新目录下的 `threads.json` 与 `categories.json`，
  未保存的修改会丢失，弹窗内有提醒）、锁定 / 重置 `threads.json` 与 `categories.json`
  （锁定用 `chattr +i`，优先 `chattr`，回退 `busybox chattr` / `/system/bin/chattr`）。
  默认目录 `/data/user/0/com.omarea.vtools/files/`。
- 重置 categories.json：首次打开应用时自动把官方类目备份到应用数据目录（`files/backups/`），
  “重置”即恢复这份备份。categories.json 无法通过切换调度档位重新拉取，如需完全重置请清除 Scene 全部数据。
- 编辑行为：在“添加应用”中显示系统应用包名。
- 管理工具：历史记录（线程 / 类目分开，可回溯 / 删除，回溯时检测版本）、导入 / 导出。
- 日志：存储上限（默认 0 即禁用）、导出日志。
- 自定义核心 / 核心簇：把应用视角下的 CPU 指定为核心数 + 从强到弱的核心簇（如 `0,1,3,5-7`），
  保存时校验核心覆盖与重名，保存 / 重置后重载应用生效；检测只在首次启动与重置时进行，
  自定义配置存于应用数据目录，不会被覆盖。
- 导入 threads.json 时询问导入文件对应的 CPU 架构（从强到弱每档核心数，如 `1,3,3`）；
  与本机不一致时可选“自适应转化”（把导入配置的核心档位映射到本机核心簇，档位不足合并到最弱档，
  效果可能一般）或修改自定义核心数后按原样导入。自适应转化只重排核心掩码，不改线程名与绑定结构：
  文件中的每个掩码先按原机各档核心归档，再映射到本机同档核心簇的并集；原机档位多于本机时，
  多出的档并入本机最弱簇，本机簇多于原机档位时，多出的簇被最弱命中的档吸收。
- 关于：开发者主页、鸣谢名单（点击可跳转项目主页）与参考资料。
- 手势导出：无待保存修改时，长按线程 / 类目页卡片进入导出模式（卡片缩小变虚），点选后按分享按钮导出，取消按钮或切页退出。

### 导入 / 导出（二级页）

- 线程规则：导出 / 导入并追加 / 导入并覆盖；类目同理。
- 导入时若检测到文件版本与当前所选不同，会弹窗提示并在确认后做“尽力而为”的版本转换
  （转换可能丢失部分配置，效果可能不佳）。顶栏 `ⓘ` 有说明。

### 版本检测与转换

- 根据字段自动识别：出现 `heaviest_thread` → Scene N1；否则有 `rr` → Scene 9；否则有 `ni` →
  Scene 9；否则有 `unity_main` → Scene 8。
- 转换规则：
  - N1 → 8/9：`heaviest_thread`+`heaviest_cores`，若线程名含 `UnityMain` 则映射为 `unity_main`，
    否则在 `heavy_thread` 空闲时提升；`rr` 丢弃。
  - 8/9 → N1：`unity_main` → `heaviest_thread = "UnityMain"` + `heaviest_cores`。
  - `ni`：仅 Scene 8 无；`rr`：仅 Scene 9 有。
  - `app_cpuset` 的 `webview`/`children`：仅 Scene 8 无。

---

## 三、如何使用

1. **编辑线程规则**：在“线程”页点右下的新增按钮，或点卡片进入编辑页。可切换默认 / 进阶模式，
   按需分配核心与线程；在包名中加入通配符 `*` 即可为所有未被其他规则命中的应用提供核心分配。

2. **编辑类目**：与编辑线程规则类似。各个类目包含的应用包名，可在编辑规则时通过类目 `Categories`
   项引用类目 ID 导入。Scene 对官方自带的类目 ID 有不同的调度策略，强烈建议不要删除或更改官方原有的
   类目 ID，只在原有类目卡片中按需增添应用包名。

3. **保存**：应用不会自动保存。所有修改（含导入）先暂存在应用内，点击线程 / 类目主页右下的保存按钮
   才写入 `threads.json` / `categories.json`。保存前会做包名 / 类目 / 线程名冲突检测，
   并生成一条可回溯的历史记录（可命名或备注）；放弃修改点击恢复按钮即可。

4. **主题**：更多 → 主题设置，切换模式与强调色；开启 Monet 后强调色/模式实时生效。

5. **导入导出**：更多 → 导入/导出，进行文件操作。导入的规则 / 类目先暂存在应用内，
   确认后点击线程 / 类目页的保存按钮才会写入文件。若导入文件的 Scene 版本与当前所选不同，
   会先弹窗做版本转换；若核心数与本机不同，还会询问原机 CPU 架构（如 `1,3,3`），
   可选择“自适应转化”把核心掩码映射到本机核心簇，或把自定义核心数改为与文件一致后按原样导入。

6. **锁定文件**：更多 → Scene 分组，锁定 / 解锁 threads.json 与 categories.json，防止 Scene 覆盖配置。

7. **首次使用**：首次打开应用会弹出使用指南，阅读 10 秒后可关闭。

---

## 四、注意事项

- 配置文件位于 `/data/user/0/com.omarea.vtools/files/`，保存 / 读取通过
  `su -mm`（全局 mount namespace）执行，避免 KernelSU 隔离导致读不到文件。
- 无 root 时写文件会降级尝试直接写并可能失败。
- **锁定文件**只影响当前配置目录下的文件；`lsattr` 精确判断锁状态。
- **版本转换是尽力而为**：非 Unity 的负载最重线程、`rr`、Scene 8 的 `ni` 等在转换时可能丢失；
  导入/回溯会先弹窗确认。
- 应用自身的设置与日志存储在应用私有 `SharedPreferences` 与 `cache/logs` 下；
  日志默认关闭（上限 0 KB），开启后写入缓存并自动修剪到上限。
- 模拟器与真机的 Scene 版本可能不同，启动时若检测到与所选版本不一致会弹窗提示；
  选择“永久忽略”后不再每次启动检测。
- 同一应用允许出现在多个类目中（Scene 官方行为），跨类目包名重复不视为冲突；
  通配 `"*"` 类目仅允许出现在官方 `"Apps"` 类目。

---

## 鸣谢

- **[Scene](https://omarea.com/#/)**（作者 [嘟嘟斯基](http://www.coolapk.com/u/734809)）—
  面向安卓系统的系统优化和性能调校工具箱，本项目的编辑对象 threads.json / categories.json 的来源。
- **[Miuix / compose-miuix-ui](https://github.com/compose-miuix-ui/miuix)**—
  HyperOS 设计语言 Compose 组件库，本项目 UI 的基石（作者 YuKongA）。
- **[MaterialKolor](https://github.com/jordond/MaterialKolor)**（作者 jordond）—
  Material You / HCT 动态配色，经 Miuix 传递使用。
- **[自定义线程编辑器](https://www.coolapk.com/feed/73634984?s=YTUyMjVkODcxNDRmM2U2ZzZhYTBkYTlkegi1656)**（作者 gyimo）—
  基于 KernelSU WebUI 的 threads.json 文件编辑模块，本应用开发的灵感来源。
- **[JetBrains](https://www.jetbrains.com/)** — Kotlin 与 Compose Multiplatform。
- **[AndroidX](https://developer.android.com/jetpack/androidx)** — `activity-compose`、`navigationevent-compose`。
- **[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)** — JSON 编解码。

## 参考资料

- [Miuix](https://compose-miuix-ui.github.io/miuix/zh_CN/)
- [Scene N1 开发者文档](https://download.omarea.com/#/versions?folder=sceneN1&dir=docs/)
- [Scene 9 开发者文档](https://download.omarea.com/#/versions?folder=scene9&dir=docs/)
- [Scene 8 开发者文档](https://download.omarea.com/#/versions?folder=scene8&dir=docs/)

## 开源协议

本项目以 **[GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0)**（GPL-3.0-or-later）开源。
GPL-3.0 为强著佐权许可：可自由使用、学习、修改与再分发；基于本项目的衍生作品（含修改后的源码分发）
必须同样以 GPL-3.0 开源并保留版权与许可声明。所依赖的 Miuix、Compose Multiplatform、
AndroidX、kotlinx.serialization 等组件均采用 Apache-2.0 或其他宽松许可，与本协议兼容。
