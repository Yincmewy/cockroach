# Cockroach · 蟑螂模组

为 Minecraft 添加会爬行、飞行和突脸的蟑螂，还可以用彩色雷电把其他生物变成蟑螂，自由调整每只蟑螂的大小。

| 项目 | 要求 |
| --- | --- |
| 当前模组版本 | 1.5.1 |
| Minecraft | 1.20.1 |
| Forge | 47.4.23 或更高的 47.x 版本 |
| Java | 17 |
| 渲染依赖 | 洛谔谔的 [Model Loader0 1.0.5](https://modrinth.com/mod/model-loader0)，已内置 |

## 主要功能

- **爬行与飞行动画**：地面移动使用原始模型的骨骼步行动画；飞行时展开鞘翅和后翅，振翅并收拢腿部，降落后平滑收翅。
- **受击逃跑与突脸**：受到有效伤害后尝试逃离；如果攻击者是玩家，还会返身俯冲到玩家眼前，悬停 2 秒后飞走。俯冲或悬停时再次受击可以打断突脸，突脸本身不造成近战伤害。
- **失眠与昼夜行为**：原版自然生成幻翼时，在每只新幻翼附近追加一只蟑螂，保留幻翼。此类蟑螂夜间寻找玩家并反复盘旋突脸，白天落地寻找阴暗处休息，不会因阳光燃烧。
- **单只实体尺寸编辑**：支持 0.05～64 倍缩放，模型、碰撞箱和眼睛高度一起变化；服务端同步给所有玩家，并保存到实体存档。
- **蟑螂转换器**：用带声音的彩色雷电把范围内的生物转换为等碰撞箱体积的蟑螂，保留名称、生命值比例和无 AI 状态。
- **分级模型与渲染优化**：三档模型精度、共享 GPU 缓存、视锥和距离剔除，并提供兼容渲染模式。
- **中文与英文界面**：包含简体中文和英文语言文件。

## 安装

1. 安装 Java 17、Minecraft 1.20.1 和兼容的 Forge 47.x。
2. 按下方说明构建项目，获取 `build/libs/cockroach-1.5.1.jar`。
3. 将这个 JAR 放入游戏实例的 `mods` 文件夹，移除旧版蟑螂模组并重启游戏。
4. 多人游戏时，客户端和服务端均安装同一版本的一体包。

本模组的模型加载前置来自 **洛谔谔（Luoee）** 的 [Model Loader0](https://modrinth.com/mod/model-loader0)，感谢原作者提供的模型加载支持。

**不需要单独安装 Model Loader0**：1.0.5 版原始依赖 JAR 已通过 Forge Jar-in-Jar 内置。`cockroach-1.5.1-slim.jar` 是不含内置依赖的开发包，日常游玩请使用不带 `-slim` 的版本。

不要同时保留多个版本的蟑螂模组。如果之前仅为本模组安装了独立 Model Loader0，可移走那一份；其他模组也依赖它时，请先检查版本兼容性。

## 玩法与命令

### 生成蟑螂

在创造模式的“刷怪蛋”分类中获取蟑螂刷怪蛋，或执行：

```mcfunction
/give @s cockroach:cockroach_spawn_egg
/summon cockroach:cockroach ~ ~ ~
```

普通刷怪蛋、转换器和默认命令生成的蟑螂平时在地面游走，不会因为玩家靠近而逃跑；受伤后尝试逃离，并向攻击它的玩家返身突脸。

要测试具有自然夜行行为的蟑螂，可以执行：

```mcfunction
/summon cockroach:cockroach ~ ~ ~ {InsomniaRoach:1b}
```

自然蟑螂夜间寻找生存或冒险模式玩家，两次俯冲之间约间隔 8～12 秒；天亮后停止主动飞行并寻找没有直射天空、方块光照不高于 7 的遮阴处休息。自然标记随存档保存，此类蟑螂在和平难度下消失。

失眠生成沿用原版幻翼对失眠时长、难度、天空、时间和生成规则的判断。命令或刷怪蛋生成的幻翼，以及读取存档中的幻翼，不会额外生成蟑螂。

### 调整大小

在创造模式的“刷怪蛋”分类中获取“蟑螂尺寸编辑器”，或执行：

```mcfunction
/give @s cockroach:size_editor
```

手持编辑器右键一只蟑螂，输入 **0.05～64** 的尺寸倍率后点击“应用”，也可恢复为 1 倍。需要创造模式或管理员权限，每次仅修改选中的实体。

管理员还可以使用以下命令：

```mcfunction
# 将最近的一只蟑螂设为 2 倍大小
/cockroach size @e[type=cockroach:cockroach,sort=nearest,limit=1] 2

# 将附近 16 格内的所有蟑螂缩小到 0.5 倍
/cockroach size @e[type=cockroach:cockroach,distance=..16] 0.5

# 修改此世界中新生成蟑螂的默认大小，不影响已有实体
/cockroach defaultsize 0.75

# 直接生成 3 倍大小的蟑螂
/summon cockroach:cockroach ~ ~ ~ {CockroachSize:3.0f}
```

默认倍率保存在该世界的 `serverconfig/cockroach-server.toml` 中。1 倍对应模型最长边约 0.9 格，2 倍约 1.8 格；超大蟑螂也有碰撞，不建议在狭小区域堆放大量 64 倍实体。

### 让蟑螂起飞

管理员可以让最近的一只蟑螂立即尝试起飞：

```mcfunction
/cockroach fly @e[type=cockroach:cockroach,sort=nearest,limit=1]
```

起飞需要头顶有空间；水中、被拴住、乘坐其他实体或关闭 AI 时不会起飞。受击后如果暂时无法起飞，蟑螂会先在地面逃跑，空间允许后再起飞。降落没有摔落伤害。

### 使用蟑螂转换器

在创造模式的“刷怪蛋”分类中获取“蟑螂转换器”，或执行：

```mcfunction
/give @s cockroach:cockroach_converter
```

手持转换器右键生物或方块即可召唤彩色雷电，空中右键可以瞄准 32 格内的生物或方块，使用间隔为 0.5 秒。

转换范围沿用原版雷电命中范围：落点水平 ±3 格、垂直 -3～+9 格。范围内的生物会被替换为蟑螂，玩家、已有蟑螂和超出尺寸范围的实体保留原样。

转换按原生物当前碰撞箱体积计算倍率，幼体也按实际大小处理：

```text
尺寸倍率 = ∛(原生物碰撞箱体积 / 1 倍蟑螂碰撞箱体积)
```

转换保留位置、名称、生命值比例和无 AI 状态，不产生死亡掉落。彩色雷电提供视觉效果和雷声音效，不造成伤害、不点火、不触发原版生物雷击变种，也不激活避雷针或去除铜块氧化。

单独测试雷电效果：

```mcfunction
/summon cockroach:rainbow_lightning
```

这个命令只生成雷电效果，生物转换由转换器执行。雷声使用游戏的“天气”音量分类。

## 客户端配置

配置文件位于游戏实例的 `config/cockroach-client.toml`：

```toml
[model]
yawOffset = 0.0
heightOffset = 0.0

[performance]
highDetailDistance = 6.0
lowDetailDistance = 20.0
maxRenderDistance = 96.0
compatibilityRenderer = false
```

| 配置项 | 用途 |
| --- | --- |
| `yawOffset` / `heightOffset` | 模型朝向与高度偏移 |
| `highDetailDistance` | 高精度模型的距离阈值，默认 6 |
| `lowDetailDistance` | 切换到低精度模型的距离阈值，默认 20 |
| `maxRenderDistance` | 最大渲染距离，默认 96 格 |
| `compatibilityRenderer` | 启用兼容渲染路径，默认关闭 |

模型精度切换按“距离 ÷ 蟑螂尺寸”判断，放大的蟑螂不会过早切换为低模。地面模型的高、中、低三档分别为 29,454 / 3,983 / 794 个三角形；飞行模型为 29,474 / 3,999 / 812 个三角形。

低配置设备可将前两个距离阈值降至 3 和 10。光影包开启、发光轮廓或手动开启兼容模式时会使用兼容渲染路径，模型精度切换仍然有效，但性能与默认 GPU 路径不同。首次遇到新的精度档位时异步加载模型，已有低精度模型可临时显示。

修改模型或贴图后可用 `F3+T` 重载资源；替换 JAR 或更新代码后需要重启游戏。

## 从源码构建

准备 **JDK 17**，首次构建需要联网下载 Gradle、Forge 和其他依赖。项目已包含 Gradle Wrapper，无需另外安装 Gradle；普通编译无需 Node.js。

```powershell
git clone https://github.com/Yincmewy/cockroach.git
cd cockroach
.\gradlew.bat build
```

Linux / macOS：

```bash
git clone https://github.com/Yincmewy/cockroach.git
cd cockroach
./gradlew build
```

产物输出到 `build/libs/`：

- `cockroach-1.5.1.jar`：包含 Model Loader0 的安装包。
- `cockroach-1.5.1-slim.jar`：不包含内置依赖的开发包。

构建所需的原始 Model Loader0 JAR 已保存在 `libs/modelloader-1.0.5.jar`，Gradle Wrapper 和模型资源也已纳入仓库。

### 开发与验证

```powershell
# 启动开发客户端
.\gradlew.bat runClient

# 运行服务端 GameTest
.\gradlew.bat runGameTestServer

# 启动客户端模型与渲染检查，需要图形环境
.\gradlew.bat -PmodelSmokeTest runClient
```

Linux / macOS 将上述命令中的 `.\gradlew.bat` 替换为 `./gradlew`。

- `build` 检查模型、内嵌贴图、走路动画缓存、模型署名和打包依赖，确认内置的 Model Loader0 与原始 JAR 一致。
- GameTest 覆盖实体生成、尺寸与存档、命令、等体积转换、交互与冷却、飞行和受击逃跑、突脸打断、失眠生成与昼夜行为，以及彩色雷电不伤害、不点火等行为。
- 客户端检查在独立的 `run-smoke/` 目录运行，真实绘制三档模型和彩色雷电，并检查 GPU / 兼容渲染、步行 / 飞行动画、贴图、尺寸相关精度切换和缓存重建。结果位于 `build/reports/model-smoke/`。
- `benchmark.txt` 是相同变换下 100 次绘制的隔离测试，计入 GPU 完成时间，取 7 次采样中位数；结果不包含 AI、地形或多人网络开销，不能直接换算为实际游戏帧率。
- GameTest 和客户端测试入口不会打入正式模组 JAR。

### 重新生成模型资源

仅重新生成模型时需要 Node.js；请使用支持 `import.meta.dirname` 的版本。

```powershell
npm.cmd ci --prefix tools --ignore-scripts
node tools/generate-lods.mjs
node tools/generate-flight-models.mjs
node tools/generate-walk-animation.mjs
```

Linux / macOS 使用 `npm ci --prefix tools --ignore-scripts`。生成后的模型和动画缓存已提交，常规构建可直接使用。

### 目录结构

```text
src/main/java/          模组入口、实体 AI、物品、命令、网络与客户端渲染
src/main/resources/     模组元数据、语言文件、模型、贴图、着色器与第三方声明
src/gametest/           服务端行为测试、客户端渲染检查与测试场景
tools/                  模型简化、飞行部件和走路动画生成脚本
libs/                   构建与内置打包使用的原始依赖 JAR
gradle/wrapper/         Gradle Wrapper
```

游戏运行目录、存档、构建产物、IDE 配置、Node.js 依赖和本地重复压缩包已通过 `.gitignore` 排除。

## 版本记录

| 版本 | 主要变化 |
| --- | --- |
| 1.5.1 | 恢复彩色雷电的原版雷声和落雷音效，每道雷电首次闪现时播放一次 |
| 1.5.0 | 玩家受击后的返身突脸、40 tick 悬停与受击打断；失眠追加蟑螂、夜间追逐和白天寻阴休息 |
| 1.4.0 | 接入原始 `Take 001` 骨骼走路动画，按移动距离驱动步频；受击后优先起飞逃跑 |
| 1.3.0 | 三维飞行、自动降落、可活动的鞘翅与后翅，以及飞行状态同步 |
| 1.1.x | GPU 缓存、三档模型精度、渲染剔除；修复中远距离模型贴图缺失 |

## 署名与许可

模组代码当前按项目元数据声明为 **保留所有权利（All Rights Reserved）**。模型及第三方组件分别遵循其自身许可。

- **蟑螂模型**：原作者 [CK](https://sketchfab.com/xcellf)，[原始作品](https://sketchfab.com/3d-models/cockroach-cead55b8aa8643d48d67240bad028592)，许可为 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)。原始 GLB 保持不变，低模、可活动飞行模型和步行动画缓存为注明修改的衍生资源，保留原作者署名与许可。
- **[Model Loader0 1.0.5](https://modrinth.com/mod/model-loader0)**：作者 **洛谔谔（Luoee）**，MIT 许可，通过 Forge Jar-in-Jar 内置原始 JAR，不做类合并或修改。
- **meshoptimizer 1.2.0**：作者 Arseny Kapoulkine，MIT 许可，仅用于模型生成工具。

完整说明见 [模型署名](src/main/resources/MODEL_CREDITS.md) 和 [第三方组件声明](src/main/resources/THIRD_PARTY_NOTICES.md)，两份声明也包含在模组安装包内。
