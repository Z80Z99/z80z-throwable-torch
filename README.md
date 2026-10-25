# Z80Z 可投掷火把

适用于 **Minecraft Java 版 1.20.1 + Forge 47.x + Java 17**。无需其他前置模组。多人游戏时，服务器和每位玩家都要安装同一个版本。

**[English](#english) | [简体中文](#简体中文)**

## 安装

1. 关闭游戏。
2. 将下载得到的 `z80z-throwable-torch-1.0.1-r2.jar` 放进对应游戏实例的 `mods` 文件夹，同一模组只保留一个版本。
3. 用 Forge 1.20.1 启动游戏。
4. 直接使用原版普通火把；不需要合成新物品。

## 操作

| 操作 | 效果 |
| --- | --- |
| 手持普通火把，短按右键后松开 | 按松开时准星的位置正常放置或交互 |
| 按住右键不足 0.5 秒 | 还没有开始蓄力，松开仍算短按 |
| 按住右键 0.5 秒后 | 显示蓄力条，手臂后摆 |
| 按住总计 1.5 秒 | 满蓄力；继续按住不再提高力度 |
| 蓄力后松开 | 消耗一根火把，沿抛物线投出 |
| 潜行右键 | 完全使用原版即时放置和交互逻辑，包括连续放置 |
| 蓄力时换物品、按潜行、左键、打开界面或切出游戏窗口 | 取消投掷，不消耗火把 |

主手、副手和左利手都支持。主手可用物品仍按原版优先级处理；例如主手盾牌正在使用时，不会抢走右键替副手投掷火把。将普通火把放在主手最直观。

短按需要松开才执行，这是为了不在长按投掷前先放掉一根火把。想要原版即时右键操作时，请按住潜行键。右键使用键可以在游戏设置中重新绑定。

## 落点规则

- 击中能支撑火把的上表面：放置原版立式火把。
- 击中能支撑壁挂火把的侧面：放置原版壁挂火把，朝向跟命中面一致。
- 放置后的火把提供原版 **14 级方块光照**，正常保存、掉落和响应方块更新。
- 不能吸附天花板；落点被占、支撑不足、在液体里、命中生物或保护模组拒绝时，生存模式会掉落原先那一根火把。
- 飞行超过 10 秒或接近世界底部时转换为掉落物，防止永久悬挂的投射物。
- 创造模式不消耗火把，放置失败不额外生成掉落物。
- 不造成伤害、不点燃实体、不破坏已有固体方块。
- 掉落物仍遵守原版规则：例如落入岩浆会烧毁，掉进虚空会消失。
- 观察者和冒险模式不提供远程投掷放置，避免绕过建造限制。

## 动作和效果

- 第三人称呈现后摆与过肩前甩；第一人称以火把后仰蓄力和前甩投掷呈现，原版挥臂保留。
- 动作阶段由服务器同步，附近其他玩家也能看到。
- 每个游戏刻生成两颗原版小火焰粒子作为火星；实际显示数量仍受游戏粒子设置和距离限制。
- 火把从眼前约 0.8 格处出手，贴墙时自动缩短出手距离；出手初期不生成火星，避免遮挡视野。
- 飞行火把复用原版火把方块模型和资源包贴图，无额外模型或贴图依赖。
- 飞行中沿弹道方向匀速前翻（约每秒 1⅓ 圈），与显示帧率无关。
- 飞行轨迹保留 6 个游戏刻，正常速度约 0.3 秒；内亮外柔，逐渐淡出，不透过实体方块显示。
- 投出复用原版投掷风声，命中复用木质闷响，避免外部音频依赖。
- 飞行火把及轨迹是自发亮的视觉效果，**不动态照亮周围方块**；真正环境照明从落地放置成功开始。

## 兼容与设置

首次启动后，客户端配置位于 `config/z80z_throwable_torch-client.toml`。直接编辑该文件需退出游戏后修改、再启动生效；也可以在游戏内 **模组列表 → Z80Z 可投掷火把 → 配置** 按钮中直接修改，保存后立即生效并写入同一文件。

| 配置 | 可用值 | 用途 |
| --- | --- | --- |
| `animation` | `OVER_SHOULDER` | 默认：后摆、过肩投掷 |
| `animation` | `VANILLA` | 不改玩家姿势，只保留原版挥臂 |
| `animation` | `EXTERNAL` | 不绘制本模组姿势，让其他模组接管；原版挥臂仍保留 |
| `trail` | `true` / `false` | 开关橙色轨迹 |
| `sparks` | `true` / `false` | 开关两颗火星 |
| `chargeBar` | `true` / `false` | 开关蓄力条 |

只接管原版 `minecraft:torch`，不改魂火火把、红石火把及其他模组物品。没有全局 Mixin 或第三方动画库依赖。

放置前检查世界边界、可替换空间、支撑、玩家建造权限和 Forge `BlockEvent.EntityPlaceEvent`。保护事件取消时恢复原方块并掉落火把。具体领地模组是否支持该标准事件仍需要组合测试；不保证与所有模组即装即兼容。

动画接口见 [动画兼容说明](docs/动画兼容说明.md)。

## 建议验收

构建检查不是进游戏实测。下面这些场景建议在新建测试世界先试一遍：

1. 生存背包放 8 根火把：短按地面只减少 1 根；长按投掷只减少 1 根。
2. 分别按住 0.3、0.6、1.5 秒：应依次正常放置、短距离投掷、远距离投掷。
3. 朝地面和四个方向的墙投掷：应成为方向正确的原版火把，洞穴立即有实际照明。
4. 朝天花板、水池、生物、已经占据的落点投掷：不伤害目标，生存模式掉落一根火把。
5. 蓄力中换快捷栏、切副手、潜行、左键、打开背包或切出窗口：不应误投和扣物品。
6. 用 F5 看第三人称后摆及过肩前甩；切换左利手、使用副手重复测试。
7. 双人服务器分别观察动作、飞行火把、落点、声音及物品数量，确认没有双重消耗。
8. 在出生点保护区或装有领地模组的禁止建造区域测试：不应放置或破坏原方块。
9. 退出世界重新进入、传送到其他维度：不应留下蓄力姿势或旧轨迹。
10. 和其他动作模组组合时，如果姿势冲突，将 `animation` 改为 `VANILLA` 或 `EXTERNAL` 后重试。

## English

A Minecraft Java 1.20.1 mod (Forge 47.x, Java 17) with no prerequisites. In multiplayer, the server and every player should run the same version. No new items are added — you throw the vanilla torch itself.

### Controls

| Action | Result |
| --- | --- |
| Hold a torch, short right-click then release | Places / interacts normally at the crosshair position |
| Hold right-click under 0.5s | Not charged yet; release still counts as a short press |
| Hold right-click past 0.5s | Charge bar appears, arm draws back |
| Held for 1.5s total | Full charge; holding longer adds nothing |
| Release while charged | Consumes one torch and throws it along an arc |
| Sneak + right-click | Fully vanilla instant place/interact, including continuous placing |
| Switch item, sneak, left-click, open a GUI or lose window focus while charging | Throw cancelled, no torch consumed |

Both hands and left-handed mode are supported. Short-press requires release so charging doesn't waste a torch first. The use key is rebindable.

### Landing rules

- Hitting a supportable top face places a vanilla standing torch; supportable sides place wall torches oriented to the face
- Placed torches emit the vanilla **level-14 block light** and save / drop / respond to updates normally
- No ceiling attachment; on invalid landings (occupied, unsupported, liquid, entity, or protection denial) survival mode drops the torch
- Projectiles convert to item drops after 10s in flight or near the world bottom; creative mode consumes nothing
- No damage, no entity ignition, no breaking of existing solid blocks
- Spectators and adventure mode get no remote placing, so build restrictions can't be bypassed

### Animation & effects

- Third-person draw-back and over-the-shoulder throw; first-person tilt-charge and forward swing, vanilla arm swing kept; server-synced so nearby players see it
- Two vanilla flame particles per tick as sparks; trajectory trail kept for 6 ticks; vanilla throw/impact sounds reused
- The flying torch reuses the vanilla torch block model and resource-pack textures — no extra assets
- The trail is a self-lit visual effect; real lighting starts when the torch places successfully

### Config

Client config at `config/z80z_throwable_torch-client.toml` (edit while the game is closed), or in game via **Mod list → Z80Z Throwable Torch → Config** (applies instantly):

| Key | Values | Purpose |
| --- | --- | --- |
| `animation` | `OVER_SHOULDER` | Default draw-back + over-shoulder throw |
| `animation` | `VANILLA` | Keep only the vanilla arm swing |
| `animation` | `EXTERNAL` | Let other animation mods take over |
| `trail` / `sparks` / `chargeBar` | `true` / `false` | Toggle trail, sparks, charge bar |

Only vanilla `minecraft:torch` is hooked — soul torches, redstone torches and modded items are untouched. Placement checks world border, replaceable space, support, build permissions and Forge `BlockEvent.EntityPlaceEvent`; cancelled events restore the block and drop the torch. Animation API: see [动画兼容说明](docs/动画兼容说明.md).
