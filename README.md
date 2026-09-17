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
