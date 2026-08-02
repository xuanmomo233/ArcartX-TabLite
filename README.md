# ArcartX-TabLite

轻量级独立 ArcartX TAB 插件，仅保留基础玩家列表与占位符解析功能。

> 本插件为 ArcartXSuite 的 Tab 模块阉割版本独立插件，用于解决产品售卖的历史遗留问题。移除了跨服联动及一切扩展功能，只保留最核心的在线玩家列表展示与占位符解析。

---

## 更新日志

### v1.1.0

**反射 → 直接 API 调用**

- `ArcartXBridge` 不再使用反射调用 ArcartX，改为直接引用 `ArcartXAPI` / `ArcartXUIRegistry` / `ClientCustomPacketEvent` 公开 API
- `PlaceholderResolver` 不再使用反射调用 PlaceholderAPI，改为直接调用 `PlaceholderAPI.setPlaceholders`
- 新增 `libs/ArcartX-2.5.36.jar` 作为编译期依赖（`compileOnly`，运行时由服务端提供）

**Bug 修复**

- **[致命] 修复 tab.yml UI 脚本死循环**：`i++` 原位于 `if(val.player.width==250)` 块内，width 不匹配时 `i` 不递增，`while` 条件恒成立导致客户端卡死
- **[致命] 修复 tab.yml UI 脚本越界**：`while(i <= packet.size())` 最后一次 `packet.get(size)` 越界，改为 `i < packet.size()`
- **修复 tab.yml 浮点陷阱**：`ID: 0` 被 ARIA 引擎解析为浮点 `0.0`，`range` 产生的 `i` 也是浮点；统一改为 `i.round` / `0.round()`
- **修复首次注册 UI 永远不触发**：`registerOrReloadUi` 原靠异常控制流，ArcartX `reload` 对未知 id 可能静默无操作；改为先 `get(uiId)` 判存在，存在→reload，不存在→register
- **修复版本号占位符替换不生效**：`build.gradle.kts` 的 jar task filter 与 processResources 产物冲突，`${project.version}` 未被替换；改用 `processResources.filter(ReplaceTokens)`

**功能改进**

- `/simpletab reload` 现在同时重新注册 `tab.yml` UI 定义，UI 定义热更新无需重启
- 新增 `refresh-interval-ticks` 配置项，可自定义刷新间隔（默认 20 ticks）
- 新增 `debug` 配置项，开启后输出每次发包日志
- 移除无效的 `sort-mode` 配置项（原只有 `name` 一个选项，switch 只有 default 分支，写了但不生效）

---

## 功能特性

- **轻量独立**：不依赖 ArcartXSuite 宿主，仅需 ArcartX 客户端前置
- **直接 API 调用**：通过 ArcartX 公开 API（`ArcartXAPI` / `ArcartXUIRegistry` / `ClientCustomPacketEvent`）与 ArcartX 交互，无反射
- **在线玩家列表**：实时展示服务器在线玩家，支持按玩家名排序与显示上限
- **占位符解析**：
  - 兼容 **PlaceholderAPI** 全部占位符（必须安装）
  - 若未安装 PAPI 的 `Expansion-player.jar` / `Expansion-server.jar` 扩展则无法使用
- **智能刷新**：
  - 定时自动刷新（间隔可配置，默认 1 秒）
  - 数据变化 diff 检测，避免重复发包
  - 支持客户端主动请求刷新（`Packet.send("TAB","update")`）
  - 客户端刷新限流保护，防止恶意高频请求
- **HUD 模式**：基于 `tab.yml` 的 ArcartX UI 定义，原生 HUD 显示

---

## 前置依赖

| 插件 | 必需 | 说明 |
|------|------|------|
| **ArcartX** | ✅ 必须 | 客户端 UI 框架前置 |
| **PlaceholderAPI** | ✅ 必须 | 占位符解析前置；若缺少 player/server 扩展，插件会自动注入回退占位符 |

---

## 安装部署

1. 下载 `ArcartX-TabLite-1.1.0.jar` 放入服务器的 `plugins/` 目录
2. 确保已安装 **ArcartX**
3. 启动服务器，插件会自动生成默认配置
4. 按需修改 `plugins/ArcartX-TabLite/tabs/online-tab.yml`

---

## 配置文件

### `tabs/online-tab.yml`

```yaml
# 是否启用该 Tab 定义
enabled: true

# UI ID 与 Packet Handler，需与 tab.yml 中定义的一致
ui-id: "tab"
packet-handler: "tab"

# 客户端刷新请求配置（UI 中 Packet.send("TAB","update") 触发）
client-refresh-packet-id: "TAB"
client-refresh-action: "update"

# 客户端刷新限流保护
client-refresh-guard:
  enabled: true
  window-ms: 1500      # 滑动窗口时长（毫秒）
  max-hits: 1           # 窗口内最大允许刷新次数
  mode: "silent"        # 超限处理方式：silent（静默忽略）/ notify（提示玩家）
  notify-message: "&cTAB 刷新过快，请稍后再试。"
  notify-cooldown-ms: 3000

# 最大显示玩家数，-1 表示不限制
max-entries: -1

# 是否倒序排序
sort-descending: false

# 是否忽略空值行
omit-blank-values: false

# 刷新间隔（ticks），默认 20（1 秒）
refresh-interval-ticks: 20

# 调试日志，开启后输出每次发包详情
debug: false

# 每行显示模板，会对每个在线玩家渲染一次
# 支持 PAPI 全部占位符；若未安装 Expansion-player / Expansion-server，插件会自动注入回退占位符
pack: "%player_name% %player_health%/%player_max_health%"
```


---

## 命令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/simpletab reload` | `simpletab.admin` | 重载插件配置（同时重新注册 tab.yml UI 定义） |

---

## 构建

本项目使用 Gradle 构建，JDK 17+ 环境：

> **编译前**：需手动将 `ArcartX-*.jar` 放入 `libs/` 目录（`compileOnly` 依赖，不打包进最终 jar，运行时由服务端提供）。jar 不包含在仓库中，见 `.gitignore`。

```bash
# Windows
gradlew.bat clean build

# Linux / macOS
./gradlew clean build
```

构建产物位于 `build/libs/ArcartX-TabLite-1.1.0.jar`。

> **编译依赖说明**：`libs/ArcartX-2.5.36.jar` 为编译期依赖（`compileOnly`），不打包进最终 jar，运行时由服务端已安装的 ArcartX 提供。ArcartX 升级后需同步更新此 jar。

---

## 作者

**墨墨墨**

- QQ: **1451759359**

---

## 声明

本插件基于 ArcartXSuite 的 Tab 模块核心逻辑进行精简与独立化，仅供授权用户用于历史产品兼容与维护场景。
