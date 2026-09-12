# 鸣谢名单

Thread Editor 建立在以下开源项目与团体之上，谨表谢意（应用内“关于”页可点击跳转）：

- **[Scene](https://omarea.com/#/)**（作者 嘟嘟斯基，[酷安主页](http://www.coolapk.com/u/734809)）
  —— 面向安卓系统的系统优化和性能调校工具箱，本项目的编辑对象 threads.json / categories.json 的来源。
- **Miuix / compose-miuix-ui**（[仓库](https://github.com/compose-miuix-ui/miuix)，作者 [YuKongA](https://github.com/YuKongA)）
  —— HyperOS / MIUI 设计语言的 Compose Multiplatform 组件库，
  本项目全部 UI（TopAppBar、Scaffold、Card、NavigationBar、TabRow、Dialog、Preference、
  NavDisplay 等）均由其提供。
- **[MaterialKolor](https://github.com/jordond/MaterialKolor)**（作者 jordond）
  —— Material You / HCT 动态配色算法的 Kotlin 实现，
  由 Miuix 内部依赖引入，实现莫奈取色。
- **[自定义线程编辑器](https://www.coolapk.com/feed/73634984?s=YTUyMjVkODcxNDRmM2U2ZzZhYTBkYTlkegi1656)**（作者 gyimo）
  —— 基于 KernelSU WebUI 的 threads.json 文件编辑模块，本应用开发的灵感来源。
- **[JetBrains](https://www.jetbrains.com/)** —— Kotlin 语言与 Compose Multiplatform 框架。
- **[AndroidX 团队](https://developer.android.com/jetpack/androidx)** —— `activity-compose`、`navigationevent-compose` 等库。
- **[Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)** —— JSON 序列化/反序列化。
- **Aliyun 与腾讯云 Maven 镜像** —— 为国内网络环境提供稳定依赖源，保障可复现构建。

> 若发现遗漏，欢迎指正；本项目作为对这些上游成果的“再创作”，深表致敬。

## 开源协议

本项目以 [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0)（GPL-3.0-or-later）开源。
所依赖的 Miuix、Compose Multiplatform、AndroidX、kotlinx.serialization 等组件
均采用 Apache-2.0 或其他宽松许可，与本项目的 GPL-3.0 兼容。
