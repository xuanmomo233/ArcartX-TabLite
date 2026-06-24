# ArcartX-TabLite

轻量级独立 ArcartX TAB 插件，仅保留基础玩家列表与占位符解析功能。

> 本插件为 ArcartXSuite 的 Tab 模块阉割版本独立插件，用于解决产品售卖的历史遗留问题。移除了跨服联动及一切扩展功能，只保留最核心的在线玩家列表展示与占位符解析。

---

## 功能特性

- **轻量独立**：不依赖 ArcartXSuite 宿主，仅需 ArcartX 客户端前置
- **在线玩家列表**：实时展示服务器在线玩家，仅支持按玩家名排序与显示上限
- **占位符解析**：
  - 兼容 **PlaceholderAPI** 全部占位符（必须安装）
  - 若未安装 PAPI 的 `Expansion-player.jar` / `Expansion-server.jar` 扩展则无法使用
- **智能刷新**：
  - 定时自动刷新（默认 1 秒）
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

1. 下载 `ArcartX-TabLite-1.0.0.jar` 放入服务器的 `plugins/` 目录
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

# 排序方式（当前仅支持 name）
sort-mode: "name"
sort-descending: false

# 是否忽略空值行
omit-blank-values: false

# 每行显示模板，会对每个在线玩家渲染一次
# 支持 PAPI 全部占位符；若未安装 Expansion-player / Expansion-server，插件会自动注入回退占位符
pack: "%player_name% %player_health%/%player_max_health%"
```


---

## 命令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/simpletab reload` | `simpletab.admin` | 重载插件配置 |

---

## 构建

本项目使用 Gradle 构建，JDK 17+ 环境：

```bash
# Windows
gradlew.bat clean build

# Linux / macOS
./gradlew clean build
```

构建产物位于 `build/libs/ArcartX-TabLite-1.0.0.jar`。

---

## 作者

**墨墨墨**

- QQ: **1451759359**

---

## 声明

本插件基于 ArcartXSuite 的 Tab 模块核心逻辑进行精简与独立化，仅供授权用户用于历史产品兼容与维护场景。
