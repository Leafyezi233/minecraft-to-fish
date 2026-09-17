<div align="center">

# 🎣 Minecraft to Fish

**每一次抛竿，都会钓上来一个"东西"。**

一个 Minecraft 1.20.1 Fabric 模组：钓鱼时**必定**召唤出一只自定义生物。

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62b47a?style=flat-square)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.16.10-dbb69c?style=flat-square)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-red?style=flat-square)](#-许可证)
[![Version](https://img.shields.io/badge/Version-0.1.1--dev-orange?style=flat-square)](#)

</div>

---

## 📖 简介

在**原版钓鱼机制之上**加了一层：每次成功钓上鱼时，除了正常的原版鱼获，还会**必定**从三种自定义生物中随机召唤一只。

三条鱼性格、体型、强度完全不同 —— 有会追着你咬的，有比鲑鱼还壮的，也有小到只有半个方块、一碰水就溜走的。

> 💡 原版掉落物（鱼、宝藏、垃圾）**完全不受影响**，模组生物是"额外"咬钩出来的。

---

## 🐟 三种生物

### ⚔️ 攻击性鱼 — Aggressive Fish
> 最常见的那个，也是最先来咬你的。

| 属性 | 值 |
|---|---|
| 生命值 | 10 ❤️ |
| 攻击力 | 3 ⚔️ |
| 碰撞箱 | 0.5 × 0.5 |
| 外观 | 原版鳕鱼（Cod） |
| 掉落物 | 攻击性鱼 |

**特点**：**距离感应跳跃** —— 离你 6 格以外时全力扑击；越靠近你跳得越慢越矮（贴脸时降到全速的 45%），像一个在蓄力试探的猎手。跟随范围 32 格，会主动锁定玩家。

---

### 💪 凶猛的鱼 — Brutal Fish
> 最稀有，也最难缠。

| 属性 | 值 |
|---|---|
| 生命值 | 16 ❤️ |
| 攻击力 | 5 ⚔️ |
| 碰撞箱 | 0.8 × 0.8 |
| 外观 | 原版鲑鱼（Salmon） |
| 掉落物 | 凶猛的鱼 |

**特点**：比攻击性鱼更大、更硬、更疼，同样带距离感应跳跃。碰上了建议直接上剑。

---

### ✨ 微缩鱼 — Shrunk Fish
> 缩小一半、自带发光、从鱼钩飞到你脚边 —— 然后拔腿就跑。

| 属性 | 值 |
|---|---|
| 生命值 | 4 ❤️ |
| 攻击力 | 无（被动） |
| 碰撞箱 | 0.25 × 0.2（正常的一半） |
| 外观 | 原版小热带鱼，**模型缩小 50%** |
| 掉落物 | 微缩鱼 |

**行为流程**：

1. 🕊️ **飞行动画** —— 咬钩瞬间从**鱼钩位置**生成，沿抛物线飞行约 0.75 秒落到你脚底（飞行中无重力，会追着移动中的玩家）
2. 🛡️ **落地保护** —— 刚生成约 1 秒内不会入水消失
3. 🐢 **慢速逃跑** —— 朝最近的水（16 格内）连蹦带跳地逃，跳跃频率和速度都刻意调慢，方便你追上
4. 💦 **入水即遁** —— 一碰到水就"噗通"一声消失，**不掉落任何东西**（它跑掉了）
5. ✨ **始终发光** —— 自带原版发光描边（和"发光"状态效果同款），在草丛和水边很好找

> 想拿到掉落物？趁它还在岸上时**打它**。跑进水里就白钓了。

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
- 目前**不能吃、不能烹饪**（纯收藏/材料，欢迎提需求）

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

1. 从 [Releases](../../releases) 下载 `minecraft_to_fish-<版本>.jar`
2. 丢进 `.minecraft/mods/` 文件夹
3. **确认同时装了 Fabric API**（否则游戏会崩）
4. 启动游戏

> ⚠️ 本模组**只需装在客户端或服务端任一侧**即可正常运行逻辑；但若在服务器使用，建议两端都装以保持一致体验。

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
│  │  └─ mixin/
│  │     └─ FishingBobberEntityMixin.java   钓鱼生成逻辑（核心）
│  └─ resources/
│     ├─ fabric.mod.json               模组元数据
│     ├─ minecraft_to_fish.mixins.json Mixin 配置
│     ├─ assets/minecraft_to_fish/     语言文件 + 物品模型
│     └─ data/minecraft_to_fish/       战利品表
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

---

## 🗺️ Roadmap

- [ ] 鱼可以烤着吃 / 有食用效果
- [ ] 自然生成（目前只能通过钓鱼获得）
- [ ] 自定义贴图（现在是复用原版贴图）
- [ ] 配置文件（免改代码调概率）

---

## 🤝 反馈与建议

欢迎提 [Issue](../../issues) 反馈问题！特别欢迎：**新生物的点子**、**平衡性建议**、**贴图**。

> ⚠️ 由于本项目保留所有权利，**不接受代码 PR**。但你的想法和建议非常欢迎 —— 提 Issue 就行，如果我采纳了会自己实现。

### ⚠️ 开发注意（给想自己改着玩的人）

如果你 fork 本仓库**仅用于个人学习和本地游玩**，请注意这条坑：

从 `src/main/resources/` **删除**文件后，务必执行 `clean build`。Gradle 的 `processResources` 只做增量复制，**不会删除**已移除的文件，旧文件会继续被打进 jar。

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
