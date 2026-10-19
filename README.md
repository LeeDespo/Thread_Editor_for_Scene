# Thread Editor

Thread Editor 是一个用于编辑 [Scene](https://omarea.com/#/) 性能配置的 Android 应用，以可视化方式管理两份文件：

- `threads.json`：线程规则与 CPU 核心分配；
- `categories.json`：应用类目，供线程规则通过类目 ID 引用。

应用包名为 `com.scenepreset.editor`，界面基于 [Miuix](https://github.com/compose-miuix-ui/miuix) 构建，支持 Scene 8 / Scene 9 / Scene N1 三代配置、跨设备 CPU 拓扑映射、历史回溯与自定义核心拓扑。

> 本文中的“Scene 版本”指 Scene 8 / 9 / N1，是编辑目标；“App 版本”指应用自身的版本；“跨架构”指不同手机的 CPU 核心拓扑，不涉及 ARM / x86 指令集转换。

## 快速开始

**下载与版本**

不自行编译时，直接下载 APK：

- 下载与更新：<https://github.com/LeeDespo/Thread_Editor_for_Scene/releases>
- 项目仓库：<https://github.com/LeeDespo/Thread_Editor_for_Scene>
- 当前源码版本：**1.0.0**（`versionCode 1`）；已发布版本：**v1.0.0**

安装步骤：

1. 从 Releases 下载 APK。
2. 在文件管理器或 `adb install -r <apk>` 中安装。系统可能提示“未知来源”，按提示允许一次。
3. 打开应用。首次读取或保存配置时，root 管理器会弹出授权请求，请允许并记住；如果没有弹窗，检查 root 管理器的授权列表。
4. 首次启动会显示使用指南，10 秒后可关闭；默认选择 Scene N1，可在“更多 → Scene 版本”中修改。

覆盖安装旧版本时需使用同一签名的包。不同签名无法覆盖安装，必须先卸载；卸载会删除应用私有数据（设置、历史、类目备份、自定义核心拓扑、日志），但不会删除 Scene 自己的配置文件。

## 运行要求

这台手机能不能用？对照下表：

| 项目 | 说明 |
| --- | --- |
| Android | 7.0 及以上（`minSdk 24`，`targetSdk 37`） |
| root | 读写 Scene 私有目录需要 root。无 root 时仍可导入、编辑、导出 JSON，但不能保存到 Scene 目录、锁定或重置文件 |
| Scene | 要编辑 Scene 已生成的配置，需要 Scene（`com.omarea.vtools`）已安装并至少生成过配置；未安装时应用仍可打开，作为 JSON 编辑器使用，但没有现成的 Scene 配置可读 |
| 配置路径 | 默认 `/data/user/0/com.omarea.vtools/files`，可在“更多 → Scene”中修改 |
| 网络 / 存储权限 | 不申请 INTERNET 或存储权限；JSON 导入 / 导出通过系统文件选择器（SAF）完成 |

无 root 时并不是完全不可用：界面、导入、编辑和导出 JSON 都能使用；受限的是对 Scene 私有文件的读取、保存、锁定和重置。读取失败时应用可能显示空列表，**如果 Scene 中确实有配置，不要直接保存**，先检查 root 授权、Scene 是否安装、配置路径是否正确；直接保存可能用空内容覆盖原文件。

## 目录

- [快速开始](#快速开始)
- [运行要求](#运行要求)
- [主要功能](#主要功能)
- [数据模型与术语](#数据模型与术语)
- [使用说明](#使用说明)
- [数据安全与换机迁移](#数据安全与换机迁移)
- [常见问题](#常见问题)
- [注意事项](#注意事项)
- [构建](#构建)
- [项目结构](#项目结构)
- [反馈与贡献](#反馈与贡献)
- [鸣谢与许可](#鸣谢与许可)

## 主要功能

- 线程规则：默认（`app_cpuset`）与进阶（`cpuset`）规则编辑，支持搜索、停用、批量导出。
- 类目管理：编辑包名与 Activity 组件，供线程规则引用。
- 保存前校验：线程规则检查包名、类目和线程名冲突；类目检查 ID 重复与通配约束。
- 版本转换：识别 Scene 8 / 9 / N1，导入和回溯时按需转换。
- 跨设备 CPU 拓扑：按原机档位映射核心掩码，或直接按原样导入。
- 历史快照：每次成功保存后生成，可回溯或删除。
- 文件管理：自定义配置路径，锁定 / 重置 `threads.json` 与 `categories.json`。
- 其他：自定义核心拓扑、主题与 Monet 取色、导入 / 导出、日志。

## 数据模型与术语

### threads.json

`threads.json` 的顶层是数组，每个元素是一条规则。规则分为两类：

- **默认规则**：使用 `app_cpuset`，面向普通应用的 main / render / other 线程；
- **进阶规则**：使用 `cpuset`，面向游戏或重载场景的线程绑定。

默认 / 进阶是同一个数组中的两种规则类型，不是同一条规则的两组字段。界面按当前类型编辑；保存时只写入当前类型对应的字段。导入的旧文件如果同时含两组字段，会按 `cpuset` 优先显示为进阶规则。

规则中没有 `cpuset` 时归入默认列表；两者都没有的规则会显示为“规则”并归入默认列表。

最小示例（N1 进阶规则与默认规则各一条）：

```json
[
  {
    "friendly": "示例应用",
    "packages": ["com.example.app"],
    "app_cpuset": {
      "main": "0-3",
      "render": "4-7",
      "other": "0-7",
      "webview": true,
      "children": true
    }
  },
  {
    "friendly": "示例游戏",
    "packages": ["com.example.game"],
    "cpuset": {
      "heaviest_thread": "UnityMain",
      "heaviest_cores": "7",
      "heavy_thread": "GameThread",
      "heavy_cores": "4-6",
      "main_thread": "0-3",
      "comm": {
        "4-7": ["RenderThread"]
      },
      "other": "0-7",
      "trashy": ["BackgroundThread"],
      "ni": ["AudioThread"]
    }
  }
]
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `friendly` | 备注名，只影响可读性，不影响匹配 |
| `packages` | 包名列表；`"*"` 表示通配 |
| `categories` | 引用的 `categories.json` 类目 ID 列表 |
| `app_cpuset` | 默认规则字段；与 `cpuset` 二选一 |
| `cpuset` | 进阶规则字段；与 `app_cpuset` 二选一 |

`app_cpuset` 子字段：

| 字段 | 说明 |
| --- | --- |
| `main` / `render` / `other` | 主线程 / 渲染线程 / 其它线程的核心掩码 |
| `webview` / `children` | 是否检测 WebView / 子进程；Scene 8 不支持 |

`cpuset` 子字段：

| 字段 | 说明 |
| --- | --- |
| `unity_main` | Scene 8 / 9 的 UnityMain 核心掩码 |
| `heaviest_thread` / `heaviest_cores` | Scene N1 的负载最重线程名与核心掩码 |
| `heavy_thread` / `heavy_cores` | 重载线程名与核心掩码 |
| `main_thread` | 主线程核心掩码 |
| `comm` | 核心掩码到线程名列表的映射 |
| `other` | 未被其它规则命中的线程使用的核心掩码 |
| `trashy` | 垃圾线程名列表 |
| `ni` | 提权线程名列表；Scene 8 不支持 |
| `rr` | 实时调度线程名列表；仅 Scene 9 支持 |

核心掩码是核心编号或范围，例如 `0-3,7`。`heaviest_thread` / `heavy_thread` 是字符串，多个线程名用 `;` 分隔；`trashy` / `ni` / `rr` 是字符串数组。写入时会省略空字段；线程规则按“默认规则 → 进阶规则 → 通配规则”排序。

### categories.json

`categories.json` 的顶层也是数组：

```json
[
  {
    "friendly": "示例类目",
    "category": "Example",
    "packages": ["com.example.app"],
    "activities": ["com.example.app.MainActivity"]
  }
]
```

| 字段 | 说明 |
| --- | --- |
| `friendly` | 备注名 |
| `category` | 类目 ID，供 `threads.json` 的 `categories` 引用 |
| `packages` | 归入该类目的包名列表 |
| `activities` | 归入该类目的 Activity 组件名列表 |

通配类目是 `packages` 中含 `"*"` 的类目。写入时通配类目排在最后，空字段会省略。

### 术语

| 术语 | 含义 |
| --- | --- |
| Scene 版本 | 编辑目标 Scene 8 / 9 / N1，不是 App 版本，也不是导入文件的文件名版本 |
| 默认规则 | 使用 `app_cpuset` 的规则 |
| 进阶规则 | 使用 `cpuset` 的规则 |
| 核心掩码 | 核心编号集合，如 `0-3,7` |
| 档位 | 原机 CPU 从强到弱的每档核心数，如 1 超大核 + 3 大核 + 4 小核写作 `1,3,4` |
| 核心簇 | 应用视角下的核心分组；来自自定义拓扑或硬件检测 |
| 跨架构 | 跨手机的 CPU 拓扑映射，不是 ARM / x86 指令集转换 |
| 恢复 | 主页“恢复”=撤销未保存修改；卡片“恢复”=重新启用已停用项 |

## 使用说明

### 首次启动

首次打开应用会显示使用指南，需等待 10 秒倒计时结束后才能关闭。首次启动默认选择 Scene N1，可在“更多 → Scene 版本”中修改。

应用启动后会检查 Scene：

- 未安装 Scene 时提示“未检测到 Scene”；应用仍可继续使用，只是没有可读取的 Scene 配置；
- 已安装 Scene 时，根据其包 `com.omarea.vtools` 的 `versionName` 识别版本；若与当前选择不一致，会提示切换、忽略或永久忽略。

选择“永久忽略”后，后续启动不再进行这项检查。手动切换 Scene 版本会立即重载应用。

### 界面与导航

底部导航为三栏：

- **线程**：规则列表；
- **类目**：类目列表；
- **更多**：设置与工具。

内容区共有四个逻辑页面：线程（默认）、线程（进阶）、类目、更多。到达方式：

- 点底栏“线程”总是回到线程（默认）页；
- 在线程页用“默认 / 进阶”分段控件切换两份规则列表；
- 在内容区横向滑动可依次切换四个逻辑页面；
- 界面没有页码指示器，底栏和分段控件是主要导航，横滑是补充操作。

编辑规则、编辑类目、主题设置、历史记录、导入 / 导出、自定义核心、关于等二级页面会全屏覆盖并滑入。它们左上角有返回箭头，也可使用系统返回键。

### 线程规则

线程页顶部显示“线程”和规则总数。搜索入口位于顶栏：展开状态下是标题旁的圆形搜索按钮，列表滚动折叠后变为顶栏图标；激活后在顶栏下方展开输入框，可搜索规则名、包名、应用名、类目 ID 与类目显示名。

页面分为：

- **默认**：编辑 `app_cpuset` 规则（主线程、渲染线程、其它线程、WebView / 子进程检测）；
- **进阶**：编辑 `cpuset` 规则（游戏或重载场景的线程绑定）。

规则卡片显示：

- 类型徽标（进阶 / 默认 / 规则）；
- 类目标签；
- 应用图标，最多显示 10 个，超出显示“...”并标出总数；
- 停用 / 恢复按钮。

停用的规则会变暗并给标题加删除线，点击不会进入编辑。**停用是本次编辑的排除标记：保存时该规则不会被写入文件，保存后它会从当前配置中消失。要找回只能依靠保存前的历史快照，或重新添加。**

右下角是新增按钮。存在未保存修改时，新增按钮上方会额外出现保存与恢复两个圆形按钮；这里的“恢复”是**撤销未保存修改**，不是重新启用停用项。

点击卡片进入编辑页。编辑页顶部可在“默认”和“进阶”之间切换，两页是并排的滑动页；切换类型后保存，只写入当前类型对应的字段：

- 基本信息：`friendly`（备注名）、`packages`（包名）、`categories`（类目 ID）。
- 默认页：`main`、`render`、`other`；Scene 8 以外还提供 `webview`、`children`。
- 进阶页：按当前 Scene 版本显示不同字段：
  - N1：`heaviest_thread` / `heaviest_cores`；
  - 8 / 9：`unity_main`；
  - 所有版本：`heavy_thread` / `heavy_cores`、`main_thread`、`comm`、`other`、`trashy`；
  - `ni`：Scene 8 以外显示；
  - `rr`：仅 Scene 9 显示。

包名选择器支持手动输入（逗号分隔）或从已安装应用列表勾选。在规则编辑页中，已被其它规则使用的包名会禁选；在“更多 → 编辑行为”中开启“显示系统应用”后，系统应用也会出现在列表里。

类目选择器支持手动输入类目 ID 或从 `categories.json` 列表勾选。已被其它规则引用的类目会禁选；通配类目（`packages` 包含 `*` 的类目）不可选，需要通配时请在规则包名中添加 `*`（通配包名规则只能包含 `*`，且不能引用类目）。

核心选择支持两种方式：

- 默认使用核心簇按钮与逐核按钮；
- 开启“更多 → 编辑行为 → 手动输入核心掩码”后，改为文本框输入。支持 `0-3,7` 这类写法，以及半角 / 全角逗号、分号、空格等分隔符；编号越界会显示错误提示。

提交前会校验：

- 至少添加一个包名或类目；
- 至少填写一项进阶或默认规则内容。实际判断是 `cpuset` 或 `app_cpuset` 非空：仅填当前版本支持的 `trashy` / `ni` / `rr`，或仅打开 `webview` / `children`（Scene 8 以外），也能通过，不要求填写核心掩码；
- 再做一次单条规则内的冲突检查。

### 类目

类目页顶部显示“类目”和类目总数，搜索入口与线程页一致，可搜索类目名、包名、应用名、Activity 组件和类目 ID。

类目卡片显示类目名、类目 ID 和包名对应的应用图标。通配类目（`packages` 包含 `*`）固定排在最后，卡片变暗，不能编辑、停用或选中导出。注意：这只限制长按列表进入的导出模式；“更多 → 导入 / 导出”里的“导出类目”会导出当前全部类目，包括通配类目。

编辑类目包含：

- `friendly`：备注名；
- `category`：类目 ID，供 `threads.json` 的 `categories` 字段引用；
- `packages`：归入该类目的包名；
- `activities`：归入该类目的 Activity 组件名。

包名选择器在类目编辑中允许跨类目重复；同一应用可以出现在多个类目里，这不算冲突。

提交时会检查：

- 非空类目 ID 是否重复；
- 非 `Apps` 类目是否使用了通配包名 `*`。

类目编辑不检测包名 / Activity 的跨类目冲突。

### 保存与恢复

应用不会自动保存。所有编辑、导入和回溯结果都先保存在内存中，只有点击主页右下角的保存按钮才会写入文件。

- 保存前先做冲突检测；没有冲突时弹出保存对话框，可填写操作名称和备注（均可留空）。
- 线程保存写入 `threads.json`，类目保存写入 `categories.json`。
- 保存成功后生成一条历史记录；线程规则按“默认规则 → 进阶规则 → 通配规则”排序，类目按“普通类目 → 通配类目”排序。
- 点击恢复按钮会丢弃未保存修改，回到上一次保存时的内容，并清除停用标记。
- 切换 Scene 版本或修改配置目录会重新加载，未保存的修改会丢失。

### 导入 / 导出

入口：**更多 → 导入 / 导出**。

线程规则：

- **导出规则**：打开多选面板，可搜索、全选 / 取消全选，默认全选。面板会列出所有线程规则（包括通配规则），勾选后通过系统文件选择器导出为 JSON。
- **导入并追加规则**：解析后在现有规则之后追加。
- **导入并覆盖规则**：用导入内容替换全部规则。

类目：

- **导出类目**：导出当前全部类目（包括通配类目）。
- **导入并追加类目** / **导入并覆盖类目**：只做 JSON 解析与合并 / 覆盖，不进行 Scene 版本识别或架构询问。

导入的线程规则会依次经过：

1. JSON 解码，并根据 `cpuset` 字段识别文件所属的 Scene 版本；
2. 若识别出的版本与当前选择的版本不同，先弹出“转换并导入”确认框；
3. 随后**无论核心数是否与本机相同**，都会弹出 CPU 架构询问；
4. 选择“自适应转化”或“按原样导入”，也可以取消；
5. 结果只暂存在应用内，需要点击线程页的保存按钮才会写入 `threads.json`。

弹窗顺序与实际处理顺序不同：先确认版本是否转换，再填写架构；实际应用时**先按填写的档位做掩码映射，再按目标 Scene 版本做字段转换**。版本字段转换可能丢弃目标版本不支持的字段；架构映射只改写核心掩码和 `comm` 键，不改变线程名。

CPU 架构弹窗要求填写**导入文件所属机型 / 芯片**的档位，按从强到弱排列，例如 1 个超大核 + 3 个大核 + 4 个小核填写 `1,3,4`。这个档位是转换的唯一依据，不会从文件内容推断，也不会被本机检测结果替换。

- “自适应转化”在输入能解析出至少一个正整数时可用；它会改写各核心掩码和 `comm` 的键，并把落到同一本机掩码的 `comm` 条目合并。
- “按原样导入”始终可用。核数不一致不会阻止导入，也不需要先修改自定义核心数。
- 如果文件引用的核心编号超过本机核心数，应用会开启“手动输入核心掩码”：
  - 按原样导入且版本转换未移除相关字段时，导入后的规则仍引用越界编号，开关保持开启且不可关闭；
  - 选择“自适应转化”并把越界编号映射到本机核心簇后，开关只是开启，可以再关闭；
  - 文件引用核数不大于本机时，该开关可自行开关。

#### 导出模式

没有未保存修改时，长按线程 / 类目列表的任意位置可进入导出模式。进入后卡片缩小、变虚，点击卡片选择，底部出现关闭与导出按钮；导出通过系统文件选择器完成。

- 有未保存修改时长按不会进入导出模式，只提示“还有修改未保存，无法进入导出模式”；提示中的“永久忽略”只关闭这条提示，仍需先保存。
- 在长按导出模式中，通配线程规则和通配类目不可选中导出；导入 / 导出页不受此限制（见上）。
- 点击关闭按钮退出；横向滑动切换到底栏的其它标签页时也会退出导出模式。

### 版本检测与转换

导入线程规则时，根据进阶规则中的 `cpuset` 字段识别版本：

- 任一规则包含 `heaviest_thread` → Scene N1；
- 否则任一规则包含 `rr` 或 `ni` → Scene 9；
- 否则任一规则包含 `unity_main` → Scene 8；
- 都没有命中时按 Scene N1 处理。

因此，只有 `app_cpuset`（默认规则）的文件也会被识别为 N1。

版本转换规则：

- **N1 → 8 / 9**：`heaviest_thread` + `heaviest_cores` 若线程名包含 `UnityMain`（不区分大小写），转换为 `unity_main`；否则在 `heavy_thread` / `heavy_cores` 都为空时提升为 `heavy_thread` / `heavy_cores`；两者都不满足时该配置被丢弃。
- **8 / 9 → N1**：`unity_main` 转换为 `heaviest_thread = "UnityMain"` 与 `heaviest_cores`。
- `rr` 只在 Scene 9 保留，转换到其它版本时丢弃。
- `ni` 在转换到 Scene 8 时丢弃。
- `app_cpuset` 的 `webview`、`children` 在转换到 Scene 8 时丢弃。

转换是**尽力而为**：目标版本没有等价字段时，对应配置会丢失。版本不同时，导入和回溯线程历史都会先弹窗确认。

### 跨架构导入

“自适应转化”按以下方式工作：

1. 把用户填写的档位（从强到弱）切分为原机核心编号区间。编号越小越靠弱端：最弱档占用最低编号，最强档占用最高编号。
2. 对文件中的每个核心掩码，找出它命中的原机档位，再映射到本机同档位的核心簇；结果是这些本机簇的并集。
3. 原机档位多于本机核心簇时，多出的档位并入本机最弱簇。
4. 本机核心簇多于原机档位时，只有掩码命中原机的最后一档（最弱档）时，多余的本机簇才由该档吸收。
5. `comm` 的键（核心掩码）同样会重写；不同原机掩码落到同一本机掩码时，线程列表会合并并去重。

本机核心簇的顺序为“从强到弱”：自定义核心拓扑按用户填写的顺序，自动检测则按最高频率从高到低排列。

### 锁定与重置

入口：**更多 → Scene**。

锁定用于防止 Scene 在切换调度档位或更新配置时覆盖你手动修改的 `threads.json` / `categories.json`。**建议在配置调整完成后开启锁定。**

- **锁定 `threads.json` / `categories.json`**：对当前配置目录下的文件设置不可变属性。读取锁状态使用 `lsattr`，加锁 / 解锁使用 `chattr +i` / `chattr -i`；缺少 `chattr` 时，若存在 `busybox` 则使用 `busybox chattr`，否则使用 `/system/bin/chattr`（二选一，不会顺序重试）。
- 应用保存文件时会临时解锁原文件，写完并原子替换后，如果原文件此前处于锁定状态，会重新加锁。
- 锁定只作用于当前配置目录；切换目录后，开关反映的是新目录下文件的状态。

重置行为不同，两个重置都有二次确认：

- **重置 `threads.json`**：删除当前 `threads.json`。删除后需要在联网状态下到 Scene 中切换一次调度档位（LP / HP / EP），让 Scene 重新生成配置文件。
- **重置 `categories.json`**：恢复应用第一次成功读取 `categories.json` 时保存的备份。它不是向 Scene 重新拉取官方配置；如果备份不存在，清除 Scene 的全部数据才能重新获取官方类目（会同时清空 Scene 的其它设置）。

类目备份按配置目录分别保存，每个路径对应一份备份。

### 历史记录

入口：**更多 → 历史记录**。

历史按“线程 / 类目”分页签，每条记录显示时间、名称 / 备注和规则条数，可执行：

- **回溯**：把快照加载到内存，仍需点击主页保存按钮才会写入文件；
- **删除**：删除该条快照。

历史快照在**保存成功后**生成，内容是本次实际写入文件的规则 / 类目；停用项不会进入快照。代码没有设置条数或容量上限，长期使用会占用应用私有存储。

回溯线程记录时，如果记录中的 Scene 版本与当前选择不同，会先弹窗确认版本转换；类目历史没有版本转换。

### 主题

入口：**更多 → 主题设置**。

- 颜色模式：跟随系统 / 浅色 / 深色；
- 启用 Monet：跟随系统壁纸动态取色；
- 强调色：默认，或蓝、绿、紫、橙、黄、粉六个预设色。

页面顶部提供主题预览。

### 自定义核心 / 核心簇

入口：**更多 → Scene → 自定义核心 / 核心簇**。

- 核心数量用步进器调整；
- 核心簇按从强到弱排列，每个簇填写名称和核心编号（如 `0,1,3,5-7`）；
- 可新增或删除核心簇。

保存时校验：

- 是否存在未命名的核心簇；
- 核心编号是否有效（非空但解析不到核心时会报错）；
- 同一核心是否属于多个簇；
- 是否所有核心都有归属；
- 核心簇名称是否重复。

首次进入该页面会显示 5 秒阅读倒计时。保存后应用会重载，后续的核心按钮、掩码范围与跨架构映射都以自定义结构为准；点击重置会删除自定义配置，恢复为真实硬件检测结果。

硬件检测结果在当前应用进程内缓存；保存或重置自定义拓扑会重载应用。

### 自定义配置路径

默认配置目录：

```text
/data/user/0/com.omarea.vtools/files
```

在“更多 → Scene → 自定义 Scene 性能调节配置文件路径”中可修改。该路径应包含 `threads.json` 与 `categories.json`；如果输入以 `/threads.json` 结尾，应用会自动去掉文件名，只保留目录。

修改路径后会立即重新加载新目录下的两个文件，**未保存的修改会丢失**。锁定状态与类目备份也跟随当前路径。

### 日志

入口：**更多 → 日志**。

- 存储上限：0 / 256 / 512 / 1024 KB，默认 `0`（禁用）；
- `0 KB` 表示禁用日志；设为 0 时会清空当前日志文件；
- 日志写入应用缓存目录 `logs/` 下的 `thread_editor.log`，超过上限后自动修剪旧内容；
- 支持导出为文本文件和清空日志。

日志默认为关闭。需要反馈问题时，先在“更多 → 日志”把上限设为 256 / 512 / 1024 KB，再复现问题，随后尽快导出；日志位于缓存目录，可能被系统清理。日志可能包含配置路径和 root 命令的输出，分享前请自行检查。

## 数据安全与换机迁移

### 保存与历史

- 应用不会自动保存。编辑、导入、回溯都只改内存，点主页保存按钮才写文件。
- 历史快照在保存成功后生成，内容是本次写入的规则 / 类目；没有条数或容量上限。
- 停用的规则 / 类目不会写入保存文件；保存后只能从更早的历史快照或外部导出中找回。
- 保存前请确认当前列表与 Scene 中的实际配置一致。读取失败时列表可能为空，直接保存会用空内容覆盖原文件。

### 卸载、签名与数据归属

下表中的“应用私有目录”和“应用缓存目录”是 Android 运行时分配给应用的目录，不是仓库内的相对路径。

| 数据 | 位置 | 卸载应用后 |
| --- | --- | --- |
| Scene 的 `threads.json` / `categories.json` | Scene 配置目录（默认 `/data/user/0/com.omarea.vtools/files`） | 保留；不属于本应用私有数据 |
| 设置项 | `SharedPreferences`（`thread_editor_prefs`） | 删除 |
| 历史记录 | 应用私有目录 `history/` 下的 `<id>.json` | 删除 |
| 类目首次备份 | 应用私有目录 `backups/` 下的 `categories_backup_<规范化路径>.json` | 删除 |
| 自定义核心拓扑 | 应用私有目录下的 `custom_topology.json` | 删除 |
| 日志 | 应用缓存目录 `logs/` 下的 `thread_editor.log` | 删除 |

不同签名的 APK 不能覆盖安装。要换签名包必须先卸载，这会删除上表中的所有应用私有数据，但不会删除 Scene 配置。

### 能导出什么

- 线程规则：导入 / 导出页可导出为 JSON；
- 类目：导入 / 导出页可导出为 JSON；
- 日志：可导出为文本文件；
- 历史记录、类目首次备份、自定义核心拓扑：**没有导出功能**。

### 换机步骤

1. 旧手机：在“更多 → 导入 / 导出”中分别导出线程规则和类目，保存到新手机能访问的位置。
2. 新手机：安装应用，授予 root，安装并至少启动一次 Scene（让配置目录存在）。
3. 在“更多 → Scene”确认配置路径正确；如 Scene 尚未生成配置，先让 Scene 生成一次。
4. 在“更多 → 导入 / 导出”中导入规则和类目；选择追加或覆盖。
5. 回到线程 / 类目页点击保存，写入 `threads.json` / `categories.json`。
6. 如有自定义核心拓扑，在新手机上重新填写；历史记录和类目首次备份没有导出功能，无法迁移；日志可以导出，但通常不需要随换机迁移。

### 重置

两个重置都会先弹确认框：

- 重置 `threads.json` 会删除文件；之后需要在联网状态下到 Scene 切换一次调度档位，让 Scene 重新生成。
- 重置 `categories.json` 只恢复应用第一次成功读取 `categories.json` 时的本地备份；没有备份时，只能清除 Scene 全部数据来获取官方配置。

## 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| 启动后列表为空，但 Scene 里明明有配置 | 多半是 root 未授权、Scene 未安装或配置路径不对。先检查这三项；确认读取成功前不要保存，否则可能用空内容覆盖原文件 |
| 保存失败 | 确认 root 已授权、当前配置路径正确、文件未被其它程序占用。应用保存只走 root shell，不会降级为普通文件写入 |
| 提示“未检测到 Scene” | 安装 Scene，或选择“永久忽略”继续使用。未安装时仍可导入 / 编辑 / 导出 JSON，但没有现成的 Scene 配置可读 |
| 长按列表不能进入导出模式 | 先保存未保存修改。提示中的“永久忽略”只关闭提示，不会跳过保存 |
| 导入提示 JSON 格式错误 | 文件顶层必须是数组；可对照[数据模型与术语](#数据模型与术语)中的示例检查字段名和括号 |
| 类目重置提示没有备份 | 备份只在应用第一次成功读取 `categories.json` 时创建；没有备份时只能清除 Scene 全部数据来重新获取官方类目 |
| 日志为空 | 日志默认禁用。先把日志上限设为 256 / 512 / 1024 KB，再复现问题并尽快导出；缓存目录可能被系统清理 |
| 启动时反复提示 Scene 版本不一致 | 在“更多 → Scene 版本”切换到实际安装的版本，或选择“永久忽略” |
| 修改配置路径后列表为空 | 新路径下没有可读的配置文件，或 root 不可用。确认路径指向包含 `threads.json` / `categories.json` 的目录 |
| 想导出通配规则 / 类目 | 长按导出模式会排除通配项；导入 / 导出页的“导出规则”“导出类目”会包含通配项 |

## 注意事项

- **必须授予 root 权限。** 应用会依次尝试常见的 `su` 路径（包括 `/system/bin/su`、KernelSU、APatch 等），优先使用 `-mm -c`，即在整个系统的 mount namespace 中执行，避免 KernelSU 的命名空间隔离导致读不到 Scene 的私有文件；不支持时会回退到 `-m -c`、`--mount-master -c`、`-c`，最后尝试 `sh -c`。单条命令超时时间为 30 秒。
- **没有 root 时**，保存、锁定、重置等写操作会失败；读取也可能得到空结果或不完整内容。`threads.json` 的读取在 root 命令失败后会尝试直接访问文件；类目的存在性判断、保存与锁定都依赖 root shell。
- **版本转换不保证无损。** 非 Unity 的 `heaviest_thread`、`rr`、转换到 Scene 8 时丢弃的 `ni` / `webview` / `children` 等都可能被丢弃。版本不同时，导入和回溯前的确认框会提示这一点。
- **Scene 版本识别只检查 `cpuset`。** 只包含 `app_cpuset` 的文件会被识别为 Scene N1；如果当前选择是 8 或 9，会出现版本转换确认。
- **通配约束：**
  - 通配包名规则只能包含 `"*"`，且不能引用类目；
  - 通配类目（`packages` 包含 `"*"`）只允许出现在名为 `Apps` 的类目；
  - 长按导出模式会排除通配项，导入 / 导出页不会（见上文）。
- **类目约束：** 非空类目 ID 不能重复；非 `Apps` 类目不能使用通配包名；同一应用允许出现在多个类目中，跨类目包名重复不视为冲突。
- **线程冲突：** 同一包名或同一类目被多条规则引用、同一条规则内线程名互为前缀但分配的核心不同，都会在保存或提交时报告；`ni`、`trashy`、`rr` 不参与线程名冲突检查。
- **应用不申请 INTERNET 或存储权限。** JSON 的导入 / 导出均通过系统文件选择器（SAF）完成；需要联网的是 Scene 自身重新生成配置的过程。

## 构建

### 环境要求

| 项目 | 要求 |
| --- | --- |
| JDK | 21（工程使用 `jvmToolchain(21)`） |
| Android SDK | `compileSdk 37`、`buildToolsVersion 37.0.0` |
| Gradle | Wrapper 9.7.1 |
| Android Gradle Plugin | 9.4.0 |
| Kotlin | 2.4.10 |
| Compose Multiplatform | 1.12.0 |
| 主要依赖 | Miuix 0.9.4-rc01、kotlinx.serialization 1.11.0、activity-compose 1.13.0 |

依赖仓库按 `google()`、`mavenCentral()`、阿里云镜像、腾讯云镜像的顺序解析；镜像用于补充，不是唯一来源。

如果命令行环境没有可用的 Java，可将 `JAVA_HOME` 指向 Android Studio 自带的 JBR（位于 Android Studio 安装目录的 `jbr` 子目录）后执行构建。

### 构建 debug APK

在项目根目录执行：

```bash
./gradlew :androidApp:assembleDebug
```

产物路径：

```text
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

安装到已连接的设备：

```bash
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Windows 下使用 `gradlew.bat`，命令与产物路径相同。

### 构建 release APK

```bash
./gradlew :androidApp:assembleRelease
```

release 变体在仓库根目录的 `keystore.properties` 包含以下配置时读取并签名：

```properties
storeFile=threadeditor-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

`storeFile` 按相对仓库根目录的路径解析。`keystore.properties` 与密钥库文件不应提交到版本库。配置签名后产物路径为：

```text
androidApp/build/outputs/apk/release/androidApp-release.apk
```

没有 `keystore.properties` 时，release 变体不会签名，生成的是未签名 APK。

### 自动化检查

- `.github/workflows/build.yml`：在 push 到 `main` 和 Pull Request 时构建 `:androidApp:assembleDebug`，并上传 debug APK 构件。
- `.github/workflows/release.yml`：在 `v*` 标签上校验标签与 `versionName` 一致，使用仓库 secrets 恢复签名文件，构建 release APK，校验签名并创建 GitHub Release。
- 当前仓库没有单元测试 / 仪器测试源码，CI 也没有配置测试或 lint 任务。提交前请至少执行 `./gradlew :androidApp:assembleDebug` 并在真机验证相关流程。

## 项目结构

```text
androidApp/   Android 应用模块
              MainActivity 入口、清单与资源，以及 root shell、SharedPreferences、
              PackageManager、CPU 拓扑探测、SAF、日志等平台实现

shared/       Kotlin Multiplatform 库模块
              commonMain 中的 Compose UI、数据接口与模型、JSON 编解码、
              Scene 版本转换、跨架构核心映射

gradle/libs.versions.toml   依赖与插件版本
```

`androidApp` 通过 `implementation(projects.shared)` 依赖 `shared`。`shared` 主要承载可复用的 Compose UI、数据接口与模型、JSON 编解码和转换逻辑；root、文件访问、应用列表等平台能力通过 `ThreadEditorContext` 由 `androidApp` 注入。

## 反馈与贡献

- 问题反馈：<https://github.com/LeeDespo/Thread_Editor_for_Scene/issues>
- 代码贡献：<https://github.com/LeeDespo/Thread_Editor_for_Scene/pulls>

反馈问题时建议附上：

- 设备型号与 Android 版本；
- Scene 版本（8 / 9 / N1）与是否安装；
- 复现步骤、预期结果与实际结果；
- 从“更多 → 日志”导出的日志。日志默认关闭，请先在日志设置中开启上限，再复现并尽快导出；缓存目录可能被系统清理，分享前请检查是否包含敏感路径。

提交代码前请至少执行：

```bash
./gradlew :androidApp:assembleDebug
```

并在真机上验证相关流程；涉及配置读写时，请同时测试锁定、无 root / root 不可用等边界情况。当前没有测试源码或 lint 任务，PR 的 CI 只构建 debug APK。保持改动聚焦，行为变化请同步更新文档。

## 鸣谢与许可

完整的鸣谢名单与链接见 [CREDITS.md](CREDITS.md)。本项目主要建立在以下工作之上：

- [Scene](https://omarea.com/#/)：`threads.json` 与 `categories.json` 的来源；
- [Miuix](https://github.com/compose-miuix-ui/miuix)：HyperOS / MIUI 设计语言的 Compose 组件库；
- [MaterialKolor](https://github.com/jordond/MaterialKolor)：经 Miuix 使用的 Material You / HCT 动态配色；
- [自定义线程编辑器](https://www.coolapk.com/feed/73634984?s=YTUyMjVkODcxNDRmM2U2ZzZhYTBkYTlkegi1656)：项目开发的灵感来源；
- [JetBrains](https://www.jetbrains.com/)：Kotlin 与 Compose Multiplatform；
- [AndroidX](https://developer.android.com/jetpack/androidx)：`activity-compose`、`navigationevent-compose` 等；
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)：JSON 编解码。

Kotlin 源文件的 SPDX 头为 GPL-3.0-or-later（[GNU GPL v3.0](https://www.gnu.org/licenses/gpl-3.0)），完整许可文本见 [LICENSE](LICENSE)（GPLv3）。仓库中的四个 Gradle 构建脚本（`build.gradle.kts`、`settings.gradle.kts`、`androidApp/build.gradle.kts`、`shared/build.gradle.kts`）的 SPDX 头标注为 Apache-2.0，与上述许可声明不一致；再分发或制作衍生作品时请同时留意这两类声明。
