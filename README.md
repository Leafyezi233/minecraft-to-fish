<div align="center">

# 🎣 Minecraft to Fish


一个 Minecraft 1.20.1 Fabric 模组：钓鱼时召唤出生物。

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62b47a?style=flat-square)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.16.10-dbb69c?style=flat-square)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-red?style=flat-square)](#-许可证)
[![Version](https://img.shields.io/badge/Version-0.4.1--dev-orange?style=flat-square)](#)

</div>

---

## 📖 简介

本模组受 **《How to Fish》（渔力全开）** 启发 —— 一款 1–4 人合作的物理模拟钓鱼游戏，核心循环是 **"钓鱼 → 杀鱼 → 卖鱼"**：你从水里钓上来的东西不会乖乖就范，它得先被你打服，才能变成钱。

目前开发版本旨在接近该游戏的游玩体验。

---

## 🎲 生成概率

每次钓上鱼时，按权重决定出哪一只 —— **100% 必出一只，且只出一只**。

| 生物 | 权重 | 概率 |
|---|:---:|:---:|
| ⚔️ 攻击性鱼 | 50 | **50%** |
| ✨ 微缩鱼 | 30 | **30%** |
| 💪 凶猛的鱼 | 20 | **20%** |

---

## 📦 物品

三种掉落物，全部注册为普通物品：

- 贴图复用原版鳕鱼 / 鲑鱼 / 热带鱼
- 位于创造模式 **「食物与饮品」** 标签页
- 目前**不能吃、不能烹饪**

---

## 💰 经济价值系统

给物品登记"价值"，并提供一套可独立运行、也能对接外部经济模组的轻量经济系统。

### 命令

主命令 `/fishvalue`，别名 `/fv`。

| 命令 | 权限 | 说明 |
|---|:---:|---|
| `/fv query <物品>` | 所有人 | 查询价值，并显示这个价是从哪来的 |
| `/fv balance [玩家]` | 所有人 / OP | 查询余额 |
| `/fv sell` | 所有人 | 卖掉**主手**的 1 个物品 |
| `/fv list [页码]` | OP | 分页列出全部已登记价值 |
| `/fv set <物品> <价值>` | OP | 临时改价（**只存内存，重启失效**） |
| `/fv reload` | OP | 重载数据包价值表 |
| `/fv give <玩家> <金额>` | OP | 加钱 |
| `/fv bridge [后端]` | OP | 查看 / 切换经济后端 |

> 💡 `/fv set` 是调试用的临时覆盖。要**持久化**改价，请写数据包（见下）。

### 数据包改价

在 `data/<命名空间>/fish_values/` 下新建任意 `.json`。下面是一个**示例**（展示格式，非模组内置值）：

```json
{
  "replace": false,
  "values": {
    "minecraft_to_fish:aggressive_fish": 25,
    "minecraft:cod": 10
  },
  "tags": {
    "#minecraft:fishes": 8
  }
}
```

- `replace: true` → 应用本文件前**清空**已累积的价值表（整合包整体重定价用）
- 多个文件按路径字典序依次应用，**后加载的覆盖先加载的**
- 玩家数据包可覆盖模组内置的 `default.json`
- 改完执行 `/fv reload` 即可生效，**无需重启**

> `tags` 段是可选的：用标签一次给一整类物品定价（如 `#minecraft:fishes` 覆盖所有鱼，包括其他模组加的鱼）。只想给具体物品定价就只写 `values`。

**模组内置的 `default.json` 只给三条模组鱼定价**（攻击性鱼 25 / 凶猛的鱼 45 / 微缩鱼 60）。原版钓获物默认**没有价值** —— 想卖原版鱼和垃圾，自己写数据包加价即可。

### 价值解析优先级

```
物品栈级覆盖（小游戏加成）  >  运行时覆盖 (/fv set)  >  精确物品价值  >  物品标签价值  >  兜底公式  >  无价值
```

- **物品栈级覆盖**由[渔轮转盘](#-渔轮转盘)和[高尔顿板](#-高尔顿板)写在鱼自己身上，只影响那一条鱼，优先级最高
- 标签价值在**多个标签同时命中**时取**最大值**，结果确定可预期
- **兜底公式默认关闭**：只有登记过的物品才有价值，经济边界清晰。想让"万物有价"，把配置文件里的 `fallbackValueEnabled` 打开

> ⚠️ **标签命名空间**：标签写错（比如用了不存在的 `#minecraft:iron_ingots`，正确的 Fabric 约定标签是 `#c:iron_ingots`）不会报错，但会**静默失效** —— 模组会在启动日志里主动警告，方便排查。

### 配置文件

首次启动生成 `config/minecraft_to_fish.json`：

| 字段 | 默认 | 说明 |
|---|---|---|
| `currencyName` | `"金币"` | 货币显示名 |
| `preferredBridge` | `"internal"` | 首选经济后端 |
| `useExternalEconomyIfPresent` | `false` | 是否自动探测外部经济模组 |
| `externalCurrencyId` | `"common-economy:default"` | 外部 API 的货币 id |
| `fallbackValueEnabled` | `false` | 是否给未登记物品兜底估值 |
| `fallbackValue` | `0` | 兜底值；`0` = 按堆叠数推导（不可堆叠 16 / 可堆叠 4） |
| `sellCommandEnabled` | `true` | 是否启用 `/fv sell` |
| `showValueHud` | `true` | 是否显示手持鱼时的价值悬浮窗（客户端） |
| `showValueInTooltip` | `true` | 是否在 tooltip 显示价值（预留给后续版本） |
| `debugLogging` | `false` | 打印逐条价值日志 |

### 价值悬浮窗（HUD）

主手或副手拿着模组里的鱼时，屏幕**右侧正中**会显示这条鱼的名字和它的价值：

```
┌──────────────┐
│ 攻击性鱼      │
│ 25 金币      │
└──────────────┘
```

- 只认模组自己的三条鱼；主手优先，主手没有才看副手
- 价格来自**服务端同步**，联机服务器上同样显示，且与服务端 `/fv query` 结果一致
- 服务端改价（`/fv reload`、`/fv set`）后会立刻推给在线客户端，**无需重连**
- 按 `F1` 隐藏 HUD 时一并隐藏；想彻底关掉就把 `showValueHud` 设为 `false`

### 独立运行 & 外部经济 API

**零依赖可运行**：内置钱包把余额存在存档里（主世界 `level.dat`），随存档备份/迁移，重启不丢。

**对接外部经济**：本模组以**反射**方式适配 [Common Economy API](https://modrinth.com/mod/common-economy)，不引入编译期依赖、不新增 maven 仓库。

- 装了就探测：把 `useExternalEconomyIfPresent` 设为 `true`
- 探测失败 / API 版本不符 → **自动降级到内置钱包**并记 WARN，**绝不崩服**
- 用 `/fv bridge` 查看当前生效的后端

**给模组作者的接口**：在 `onInitialize()` 里注册自己的经济后端即可被本模组使用：

```java
EconomyBridgeRegistry.register(myBridge);   // 实现 EconomyBridge 接口
```

之后把 `preferredBridge` 设成你的 `id()`，或用 `/fv bridge <id>` 切换。

---

## 🎡 渔轮转盘

一个**小游戏方块**：把鱼放进去，转一把，运气好价值翻倍，运气不好鱼就没了。

### 获取方式

**没有合成配方**，只能通过创造模式物品栏（**「功能方块」**标签页）或 `/give` 获得。

```
/give @s minecraft_to_fish:fish_wheel
```

### 玩法

**没有界面**，全部靠手势操作：

| 手势 | 结果 |
|---|---|
| 手持鱼右键 | 放入（消耗 1 条） |
| 空手右键 | 开转 |
| 潜行 + 空手右键 | 把鱼取出、掉在地上 |
| 转动中右键 | 一律拒绝 |

另外，**把鱼丢在盘上**也会被自动吸入（只吸模组鱼，且只在盘面空着时吸）。

> ⚠️ 「潜行取回」必须**空手**。原版在潜行且手上拿着东西时根本不会调用右键逻辑，所以这个手势天然只能是空手潜行。

| 扇区 | 概率 | 结果 |
|---|:---:|---|
| ×2 | 30% | 鱼的价值翻倍，**鱼留在盘上** |
| ×5 | 5% | 鱼的价值 ×5，**鱼留在盘上** |
| 未中奖 | 65% | **鱼消失**，没有任何返还 |

中奖后鱼**留在盘上**，可以接着转，也可以随时拿走。加成写在鱼自己身上，所以：

- HUD 悬浮窗显示的是**加成后**的价值
- `/fv sell` 按**加成后**的价值结算
- 两条同种鱼各自的加成**互不影响**

> ⚠️ 期望值 = (2×30 + 5×5 + 0×65) / 100 = **0.85**。长期玩下去价值必然衰减 —— 这是刻意设计的，转盘不是印钞机。

### 提示

这几种情况会有提示，且**不消耗鱼**：

| 时机 | 情况 | 提示 |
|---|---|---|
| 放入时 | 盘上已有鱼 | 盘上已经有一条鱼了 |
| 放入时 | 转动中 | 转盘正在转动，请稍候 |
| 放入时 | 放的不是模组里的鱼 | 只能放入本模组的鱼 |
| 放入时 | 这条鱼没有登记价值 | 这条鱼没有登记价值，无法参与转盘 |
| 开转时 | 盘上没有鱼 | 转盘是空的，请先放入一条鱼 |
| 开转时 | 冷却中 | 转盘刚转过，请稍候 |
| 开转时 | 转动中 | 转盘正在转动，请稍候 |
| 取回时 | 转动中 | 转盘正在转动，请稍候 |
| 取回时 | 盘上没有鱼 | 转盘是空的，请先放入一条鱼 |

另外，若把 `wheelEnabled` 设为 `false`，右键会提示「渔轮转盘已在配置中关闭」而不是毫无反应。

放入时**不限制**放什么，钻石也能放进去 —— 但会被拒绝并提示。这样你随时能自己试出这条分支。

### 物品安全

**鱼不会丢。** 方块被拆时，盘上的鱼会**掉出来**（不吞不掉）；服务端在转动途中被关掉时，鱼**原样保留在盘上**，只是把未落地的结算作废、盘面复位为空闲。

> 💡 鱼现在存在**方块实体**里（不是某个玩家的界面里），所以转盘是**公共设施**：一个坐标只有一份状态，全场玩家看到的是同一个盘面。收益加在鱼身上，**谁捡到算谁的**。

### 配置

`config/minecraft_to_fish.json` 里的转盘字段：

| 字段 | 默认 | 说明 |
|---|---|---|
| `wheelEnabled` | `true` | 是否启用渔轮转盘方块 |
| `wheelSectors` | ×2/×5/未中奖 | 扇区表，见下 |
| `wheelSpinDurationMs` | `2000` | 转盘旋转动画时长（毫秒） |
| `wheelSpinCooldownMs` | `500` | 两次抽奖的冷却（毫秒） |
| `wheelMaxValue` | `0` | 单条鱼的价值上限；`0` = 不限制 |
| `wheelRequireModFish` | `true` | 是否只允许放模组里的鱼 |

扇区表长这样，**权重只有相对大小有意义**，扇区在盘面上占的角度按权重比例分配：

```json
"wheelSectors": [
  { "multiplier": 2, "weight": 30, "color": -11557935, "labelKey": "x2" },
  { "multiplier": 5, "weight": 5,  "color": -2838729,  "labelKey": "x5" },
  { "multiplier": 0, "weight": 65, "color": -9737365,  "labelKey": "lose" }
]
```

- `multiplier` = `0` 表示未中奖；想加 ×10 就往数组里加一条，**不用改代码**
- `color` 是 ARGB 整数；写 `0` 会自动分配颜色
- **期望值 ≥ 1 不会被拒绝** —— 你想配成对玩家有利的也行，只会在日志里打一条 WARN 提醒
- 只有**结构上不可用**的配置才会回退到内置默认表（空列表、负倍率、权重全为 0）

---

## 🧲 高尔顿板

第二个**小游戏方块**：球从顶部落下，在钉子之间左右乱撞，最后掉进哪个槽位就是哪个结果。

与转盘的**根本区别**：转盘是「按权重抽签」，高尔顿板是**真实的物理随机过程** —— 概率不是你配的，是二项分布算出来的。

### 获取方式

**没有合成配方**，只能通过创造模式物品栏（**「功能方块」**标签页）或 `/give` 获得。

```
/give @s minecraft_to_fish:fish_galton
```

> ⚠️ **放置时上方必须留空**。盘面竖着排下 6 排钉子，渲染会**向上超出 1 格**（到约 y≈1.95）。上方有方块时放置会被拒绝并提示「上方需要留出空间」。碰撞箱仍然只有下面那块薄底板，**不是**两格高的墙 —— 你可以贴着板站。

### 玩法

手势与转盘**完全一致**（手持鱼右键放入、空手右键开落、潜行空手取回），同样支持「把鱼丢在板上自动吸入」。

默认 7 个槽位（6 排钉子），概率来自**对称二项分布** `Binomial(6, 0.5)`：

| 槽位 | 概率 | 结果 |
|---|:---:|---|
| 最左 / 最右（0 / 6） | 1.5625% | ×10 |
| 次左 / 次右（1 / 5） | 9.375% | ×3 |
| 中间三个（2 / 3 / 4） | 78.125% | 未中奖，**鱼消失** |

> ⚠️ 期望值 = (2×1×10 + 2×6×3) / 64 = **0.875**，中奖率 **21.875%**。
> 与转盘（期望 0.85、中奖率 35%）相比：高尔顿板**赢面更小，但边缘是 ×10 的大奖**。两个游戏因此是真正的取舍，而不是数值强弱之分。

### 为什么概率配不出来

球的落点槽位 = 它**往右弹的次数**。每一排独立地以 `galtonPegRightChance` 往右弹，所以落点服从 `Binomial(排数, p)`。

服主能调的只有**倍率**（赔率）和 `galtonPegRightChance`（整条分布的偏斜）—— **配不出**「让第 3 个槽位占 40%」。这不是限制，而是这个游戏的玩法内核。

**排数由槽位数推导**（`排数 = 槽位数 - 1`），不单独配置，所以「排数和槽位数不匹配」这类配置错误从根上不存在。

### 结果不会剧透

球在下落全程**所有槽位都是暗的**，只有球**落地那一刻**才亮起落点那一格。

这一点是刻意做成结构上不可错的：服务端在开落当刻就掷好路径（客户端要靠它画轨迹），但「结果已定」与「结果已揭晓」是两个概念。渲染读的是专门的 `revealedSlot()` —— 下落中它返回「无」，所以「提前剧透」不是「记得别用错方法」的约定，而是**做不到**。

同理，开落音效**与输赢无关**（胜负音在落地才播），不会用耳朵提前剧透。

### 音效

- **撞钉音**：音高随下落**升高**（0.80 → 1.60，跨一个八度），因为球是自由落体、越掉越急，听感与画面加速对上
- **整体移调**：每次下落整体移调 ±6%，连续几次不会听起来一模一样。用的是服务端下发的**下落序号**而不是随机数 —— 撞钉音是各客户端本地播的，用随机数会导致联机时每人听到的调不同
- **开落音**：拨杆「咔哒」声，音高 0.70 **低于**第一颗钉子，你能分清「刚开始掉」和「砸到钉子了」
- **落地音**：中奖是明亮的原版升级音阶，未中奖是压到 0.60 的闷响 —— 不看屏幕也能听出结果

> 💡 音效是**有距离衰减的定位音**，会随你走远而变轻。音量倍率由 `galtonSoundVolume` 控制。

### 配置

`config/minecraft_to_fish.json` 里的高尔顿板字段：

| 字段 | 默认 | 说明 |
|---|---|---|
| `galtonEnabled` | `true` | 是否启用高尔顿板方块 |
| `galtonSlots` | 7 槽表 | 槽位表（**从左到右**），见下 |
| `galtonPegRightChance` | `0.5` | 每排**往右弹**的概率；`0.5` 为对称分布 |
| `galtonDropDurationMs` | `1600` | 球从板顶落到槽位的时间（毫秒） |
| `galtonCooldownMs` | `500` | 两次开落之间的冷却（毫秒） |
| `galtonSoundVolume` | `1.0` | 音效音量倍率；`0` = **完全静音** |
| `galtonMaxValue` | `0` | 单条鱼的价值上限；`0` = 不限制 |
| `galtonRequireModFish` | `true` | 是否只允许放模组里的鱼 |

槽位表长这样，**⚠️ 这里没有权重** —— 概率是算出来的，你只决定「落在这一格给几倍」：

```json
"galtonSlots": [
  { "multiplier": 10, "color": -2838729, "labelKey": "x10" },
  { "multiplier": 3,  "color": -11557935, "labelKey": "x3" },
  { "multiplier": 0,  "color": -9737365,  "labelKey": "lose" },
  { "multiplier": 0,  "color": -9737365,  "labelKey": "lose" },
  { "multiplier": 0,  "color": -9737365,  "labelKey": "lose" },
  { "multiplier": 3,  "color": -11557935, "labelKey": "x3" },
  { "multiplier": 10, "color": -2838729,  "labelKey": "x10" }
]
```

- 数组**从左到右**对应盘面上从左到右的槽位，**数组长度就是槽位数**
- 想改成 5 槽（4 排）就删掉两条 —— 排数会自动跟着变，**不用改代码**
- `color` 是 ARGB 整数；写 `0` 会自动分配颜色
- `galtonPegRightChance` 取 `0` 或 `1` 是合法配置（球必然全落一端），但会让游戏失去随机性
- 同样**期望值 ≥ 1 不回退**，只打 WARN；只有**结构上不可用**才回退（空列表、负倍率、有效槽位少于 2 个、槽位超过 25 个）

> ⚠️ 改了配置**要重启服务器**才生效（槽位表只在初始化时构建一次）。

---

## 🚀 安装

### 前置要求

| 需要 | 版本 |
|---|---|
| Minecraft | **1.20.1** |
| Fabric Loader | ≥ 0.15.0 |
| [Fabric API](https://modrinth.com/mod/fabric-api) | 任意（必需） |
| Java | ≥ 17 |

### 步骤

1. 构建模组
2. 丢进 `.minecraft/mods/` 文件夹
3. **确认同时装了 Fabric API**（否则游戏会崩）
4. 启动游戏

---

## 🔨 从源码构建

```bash
git clone https://github.com/Leafyezi233/minecraft-to-fish.git
cd minecraft-to-fish

# Windows
gradlew.bat build

# macOS / Linux
./gradlew build
```

产物：`build/libs/minecraft_to_fish-<版本>.jar`

**开发时启动游戏**（自动加载本模组）：

```bash
gradlew.bat runClient     # Windows
./gradlew runClient       # macOS / Linux
```

首次构建会联网下载 Gradle、JDK 17 和全部依赖，需要耐心等待。

> 🌏 **国内用户**：`gradle.properties` 里已预置 **BMCLAPI 镜像**配置，可绕过 Mojang 域名被墙的问题。如果你能直连 Mojang，删掉那 4 行 `loom_*` 配置即可恢复官方下载。

---

## 🛠️ 技术栈

| 项 | 版本 |
|---|---|
| Minecraft | 1.20.1 |
| Yarn 映射 | 1.20.1+build.10 |
| Fabric Loader | 0.16.10 |
| Fabric API | 0.92.12+1.20.1 |
| Fabric Loom | 1.6.12 |
| Gradle | 8.8（wrapper 已内置） |
| Java | 17（Gradle toolchain 自动处理） |

---

## 📂 项目结构

```
minecraft-to-fish/
├─ src/main/
│  ├─ java/com/leafyezi233/minecrafttofish/
│  │  ├─ MyMod.java                    通用入口（注册实体/物品/属性）
│  │  ├─ MyModClient.java              客户端入口（注册渲染器）
│  │  ├─ entity/
│  │  │  ├─ ModEntities.java           实体类型注册（含碰撞箱尺寸）
│  │  │  ├─ AggressiveFishEntity.java  攻击性鱼
│  │  │  ├─ BrutalFishEntity.java      凶猛的鱼
│  │  │  ├─ TimidFishEntity.java       微缩鱼（含飞行动画 + 逃跑 AI）
│  │  │  └─ client/                    三个渲染器
│  │  ├─ item/ModItems.java            物品注册
│  │  ├─ economy/                      经济价值系统（可独立运行）
│  │  │  ├─ ModEconomy.java            装配入口
│  │  │  ├─ InternalEconomyState.java  内置钱包（存档持久化）
│  │  │  ├─ value/                     价值表：加载 / 解析 / 兜底
│  │  │  ├─ bridge/                    经济后端 SPI + 外部 API 反射适配
│  │  │  ├─ net/                       价值表服务端同步（客户端 HUD 用）
│  │  │  ├─ client/                    HUD 悬浮窗 + 客户端价值表镜像
│  │  │  ├─ command/                   /fishvalue 命令树
│  │  │  └─ config/                    配置读写
│  │  ├─ game/                         两个小游戏的公共基类
│  │  │  ├─ FishGameBlockEntity.java   抽象基类：鱼持有 / 吸入 / 中断恢复 / 同步
│  │  │  ├─ GameSupport.java           毫秒→刻换算、模组鱼判定
│  │  │  ├─ GameState.java             空闲 / 已就绪 / 演出中
│  │  │  ├─ GameInteractionResult.java 交互结果枚举
│  │  │  └─ client/GameMesh.java       公共顶点绘制工具（白贴图 + 顶点色）
│  │  ├─ wheel/                        渔轮转盘（小游戏方块）
│  │  │  ├─ ModBlocks.java             方块 + 方块实体注册
│  │  │  ├─ FishWheelBlock.java        手势交互（放入 / 开转 / 取回）+ 破坏兜底
│  │  │  ├─ FishWheelBlockEntity.java  权威逻辑：抽奖 / 延迟结算 / 物品栈加成
│  │  │  ├─ WheelTable.java            扇区表：权重归一化 / 角度分配 / 抽取
│  │  │  ├─ WheelSector.java           扇区定义
│  │  │  ├─ WheelAngle.java            角度换算（朝向 / 扇区 / 落点）唯一来源
│  │  │  ├─ StackValueOverride.java    物品栈级价值覆盖（NBT）
│  │  │  ├─ net/                       扇区表服务端同步
│  │  │  └─ client/                    盘面渲染 + 客户端扇区表镜像
│  │  ├─ galton/                       高尔顿板（小游戏方块）
│  │  │  ├─ GaltonBlocks.java          方块 + 物品 + 方块实体注册
│  │  │  ├─ GaltonBlock.java           手势交互 + 上方留空校验 + 破坏兜底
│  │  │  ├─ GaltonBlockItem.java       放置被拒时给出「上方需要空间」提示
│  │  │  ├─ FishGaltonBlockEntity.java 权威逻辑：掷路径 / 延迟结算 / 揭晓时机
│  │  │  ├─ GaltonBoard.java           槽位表：二项分布概率 / 期望值 / 几何
│  │  │  ├─ GaltonSlot.java            槽位定义（倍率 / 颜色 / 文案键）
│  │  │  ├─ GaltonPath.java            路径模型：轨迹与落点是同一变量的两种读法
│  │  │  ├─ GaltonGeometry.java        盘面几何（钉子 / 槽位 / 球该画在哪）
│  │  │  ├─ GaltonSound.java           音高与音量的纯函数
│  │  │  ├─ GaltonConstants.java       路径 / 哨兵值常量
│  │  │  ├─ net/                       槽位表服务端同步
│  │  │  └─ client/                    盘面渲染 + 客户端槽位表镜像
│  │  └─ mixin/
│  │     └─ FishingBobberEntityMixin.java   钓鱼生成逻辑（核心）
│  └─ resources/
│     ├─ fabric.mod.json               模组元数据
│     ├─ minecraft_to_fish.mixins.json Mixin 配置
│     ├─ assets/minecraft_to_fish/     语言文件 + 方块 / 物品模型
│     └─ data/minecraft_to_fish/       战利品表 + 默认价值表
├─ gradle.properties                   所有版本号集中在此
├─ build.gradle                        依赖与构建配置
└─ README.md                           本文档
```

> 💡 两个小游戏共用 `game/FishGameBlockEntity`：鱼的持有、丢在地上被自动吸入、
> 演出中断后的恢复、状态同步都只写一遍。各自只实现「怎么抽」「怎么演」「怎么结算」。

---

## 🔧 自定义 / 二次开发

### 改生成概率

`FishingBobberEntityMixin.java` 顶部的权重常量：

```java
private static final int WEIGHT_AGGRESSIVE = 50;  // 攻击性鱼
private static final int WEIGHT_BRUTAL     = 20;  // 凶猛的鱼
private static final int WEIGHT_TIMID      = 30;  // 微缩鱼
```

权重是**相对值**，改成 `1 / 1 / 1` 就是三等分，不用凑够 100。

### 改微缩鱼大小

| 想改什么 | 改哪里 |
|---|---|
| 视觉大小 | `TimidFishEntityRenderer.java` → `MODEL_SCALE = 0.5f` |
| 碰撞箱 | `ModEntities.java` → `EntityDimensions.fixed(0.25f, 0.2f)` |

> 两者建议同步改，否则会出现"看着很大却撞不到"的割裂感。

### 改生物强度

各实体类里的 `createXxxAttributes()`：`GENERIC_MAX_HEALTH` / `GENERIC_ATTACK_DAMAGE` / `GENERIC_FOLLOW_RANGE`。

### 改物品价值

不用改代码 —— 写数据包 `fish_values/*.json`，或临时用 `/fv set`。详见 [💰 经济价值系统](#-经济价值系统)。

### 改转盘概率

不用改代码 —— 改配置里的 `wheelSectors` 权重。详见 [🎡 渔轮转盘](#-渔轮转盘)。

### 改高尔顿板

不用改代码 —— 改配置里的 `galtonSlots`（赔率 / 槽位数）和 `galtonPegRightChance`（分布偏斜）。详见 [🧲 高尔顿板](#-高尔顿板)。

> ⚠️ 高尔顿板的**概率配不出来**：它由 `Binomial(槽位数 - 1, p)` 决定，你只能改赔率和偏斜。这是刻意的玩法内核，不是缺功能。

---

## 🗺️ Roadmap

- [ ] 鱼可以烤着吃 / 有食用效果
- [ ] 自然生成（目前只能通过钓鱼获得）
- [ ] 自定义贴图（现在是复用原版贴图）
- [ ] 图形化配置界面（目前是手改 `config/minecraft_to_fish.json`）
- [ ] 物品价值 tooltip（客户端同步已预留）
- [ ] 批量卖鱼 `/fv sellall` + 卖出确认
- [ ] 配方自动估值（合成物 = 材料价值之和）
- [ ] 渔轮转盘的盘面贴图（现在盘面由方块实体渲染器用顶点色绘制，物品图标仍复用原版金块）
- [ ] 小游戏方块底座的专属贴图（现在仍复用原版深色橡木木板）
- [ ] 小游戏方块的合成配方

---

## 🤝 反馈与建议

欢迎提 [Issue](../../issues) 反馈问题！特别欢迎：**新生物的点子**、**平衡性建议**、**贴图**。


---

## 📄 许可证

**版权所有 © 2026 Leafyezi233。保留所有权利（All Rights Reserved）。**

本模组**未**采用任何开源许可证。你可以：

- ✅ 下载并**在游戏中游玩**本模组（包括在多人服务器上使用）
- ✅ 在**不修改文件**的前提下分享本模组的**原始下载链接**（请指向本仓库的 Releases 页面）
- ✅ 在视频、直播、整合包介绍中展示或提及本模组

未经作者**事先书面许可**，你**不得**：

- ❌ 重新分发本模组的 jar 文件（包括打包进整合包、网盘转载、二次上传）
- ❌ 修改、反编译、反混淆本模组，或基于其创作衍生作品
- ❌ 将本模组的代码或素材用于你自己的项目
- ❌ 移除或篡改本模组中的版权声明与作者信息
- ❌ 将本模组用于任何商业用途

### 想用在整合包里？

整合包作者请**先开 [Issue](../../issues) 联系我**取得授权，通常都会同意，只是希望事先知情。

---

本项目**不是**官方 Minecraft 产品，未经 Mojang 或 Microsoft 批准或关联。
