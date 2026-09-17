<div align="center">

# 🎣 Minecraft to Fish

**每一次抛竿，都会钓上来一个"东西"。**

一个 Minecraft 1.20.1 Fabric 模组：钓鱼时召唤出生物。

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62b47a?style=flat-square)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.16.10-dbb69c?style=flat-square)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-red?style=flat-square)](#-许可证)
[![Version](https://img.shields.io/badge/Version-0.1.1--dev-orange?style=flat-square)](#)

</div>

---

## 📖 简介

本模组受 **《How to Fish》（渔力全开）** 启发 —— 一款 1–4 人合作的物理模拟钓鱼游戏，核心循环是 **"钓鱼 → 杀鱼 → 卖鱼"**：你从水里钓上来的东西不会乖乖就范，它得先被你打服，才能变成钱。

目前开发版本旨在接近该游戏的游玩体验。

---

## 🎲 生成概率

每次钓上鱼时**只摇一次骰子**，按权重决定出哪一只 —— **100% 必出一只，且只出一只**。

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
运行时覆盖 (/fv set)  >  精确物品价值  >  物品标签价值  >  兜底公式  >  无价值
```

- 标签价值在**多个标签同时命中**时取**最大值**，结果确定可预期
- **兜底公式默认关闭**：只有登记过的物品才有价值，经济边界清晰。想让"万物有价"，把配置文件里的 `fallbackValueEnabled` 打开

> ⚠️ **标签命名空间**：标签写错（比如用了不存在的 `#minecraft:iron_ingots`，正确的 Fabric 约定标签是 `#c:iron_ingots`）不会报错，但会**静默失效** —— 模组会在启动日志里主动警告，方便排查。

### 配置文件

首次启动生成 `config/minecraft_to_fish.json`：

| 字段 | 默认 | 说明 |
|---|---|---|
| `currencyName` | `"渔币"` | 货币显示名 |
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
│ 25 渔币      │
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
│  │  └─ mixin/
│  │     └─ FishingBobberEntityMixin.java   钓鱼生成逻辑（核心）
│  └─ resources/
│     ├─ fabric.mod.json               模组元数据
│     ├─ minecraft_to_fish.mixins.json Mixin 配置
│     ├─ assets/minecraft_to_fish/     语言文件 + 物品模型
│     └─ data/minecraft_to_fish/       战利品表 + 默认价值表
├─ gradle.properties                   所有版本号集中在此
├─ build.gradle                        依赖与构建配置
└─ README.md                           本文档
```

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

---

## 🗺️ Roadmap

- [ ] 鱼可以烤着吃 / 有食用效果
- [ ] 自然生成（目前只能通过钓鱼获得）
- [ ] 自定义贴图（现在是复用原版贴图）
- [ ] 图形化配置界面（目前是手改 `config/minecraft_to_fish.json`）
- [ ] 物品价值 tooltip（客户端同步已预留）
- [ ] 批量卖鱼 `/fv sellall` + 卖出确认
- [ ] 配方自动估值（合成物 = 材料价值之和）

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
