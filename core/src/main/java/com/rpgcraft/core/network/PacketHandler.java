package com.rpgcraft.core.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络包注册管理器
 * <p>
 * 负责将模组所有自定义网络包注册到 NeoForge 的网络系统中。
 * 在主类构造函数中通过 {@code modEventBus.addListener(PacketHandler::register)} 挂载到 Mod 事件总线。
 * <p>
 * 注册时机为模组加载阶段（FML lifecycle），早于游戏启动，确保网络包在玩家连接前就已准备就绪。
 */
public class PacketHandler {

    /**
     * 网络包注册回调
     * <p>
     * 在 Mod 事件总线上监听 {@link RegisterPayloadHandlersEvent} 事件（自动检测为 Mod 总线事件）。
     * 通过 {@link PayloadRegistrar} 注册每个网络包的方向和处理器。
     *
     * @param event NeoForge 提供的网络包注册事件
     */
    public static void register(final RegisterPayloadHandlersEvent event) {
        // 创建注册器，参数是网络协议版本号。
        // 如果客户端和服务端的协议版本不匹配，NeoForge 会拒绝连接以防止数据不同步。
        // v2：复合职业引入，SyncProfessionStatePacket 的节点前置字段由单 nullable id 改为 id 列表。
        final PayloadRegistrar registrar = event.registrar("2");

        // 注册从服务端发往客户端的属性同步包（playToClient）
        // 参数说明：
        //   TYPE        —— 包的类型标识，用于路由
        //   STREAM_CODEC—— 包的序列化/反序列化器
        //   handle      —— 客户端收到包后的处理方法引用
        registrar.playToClient(
                SyncPlayerAttributePacket.TYPE,
                SyncPlayerAttributePacket.STREAM_CODEC,
                SyncPlayerAttributePacket::handle
        );

        // 注册全量属性快照同步包（服务端 → 客户端，用于角色界面）
        registrar.playToClient(
                SyncAttributeSnapshotPacket.TYPE,
                SyncAttributeSnapshotPacket.STREAM_CODEC,
                SyncAttributeSnapshotPacket::handle
        );

        // 注册角色界面请求包（客户端 → 服务端）
        // 客户端按键时发送，服务端创建全量快照并回传
        registrar.playToServer(
                RequestCharacterScreenPacket.TYPE,
                RequestCharacterScreenPacket.STREAM_CODEC,
                RequestCharacterScreenPacket::handle
        );

        // 属性点分配请求包（客户端 → 服务端）
        // 客户端在角色界面点击 [+] 按钮时发送，委托 attributepoints 模块校验+应用
        registrar.playToServer(
                AllocateAttributePointPacket.TYPE,
                AllocateAttributePointPacket.STREAM_CODEC,
                AllocateAttributePointPacket::handle
        );

        // 属性点模块配置同步包（服务端 → 客户端）
        // 推送 allow_decrease 配置（是否允许减少属性点），由 attributepoints 模块在登录/reload 时发送
        registrar.playToClient(
                SyncAttributePointsConfigPacket.TYPE,
                SyncAttributePointsConfigPacket.STREAM_CODEC,
                SyncAttributePointsConfigPacket::handle
        );

        // 职业面板状态请求包（客户端 → 服务端）
        // 客户端按 P 键打开职业面板时请求完整职业状态
        registrar.playToServer(
                RequestProfessionStatePacket.TYPE,
                RequestProfessionStatePacket.STREAM_CODEC,
                RequestProfessionStatePacket::handle
        );

        // 职业面板状态同步包（服务端 → 客户端）
        // 推送完整职业状态（池/等级/解锁/树元数据）供职业面板渲染
        registrar.playToClient(
                SyncProfessionStatePacket.TYPE,
                SyncProfessionStatePacket.STREAM_CODEC,
                SyncProfessionStatePacket::handle
        );

        // 职业模块配置同步包（服务端 → 客户端）
        // 推送副职业解锁消耗/默认等级上限/降级开关，由 profession 模块在登录/reload 时发送
        registrar.playToClient(
                SyncProfessionConfigPacket.TYPE,
                SyncProfessionConfigPacket.STREAM_CODEC,
                SyncProfessionConfigPacket::handle
        );

        // 职业动作请求包（客户端 → 服务端）
        // 投入经验/进阶/切换主职/设置副职/切换副职业开关，服务端权威校验
        registrar.playToServer(
                ProfessionActionPacket.TYPE,
                ProfessionActionPacket.STREAM_CODEC,
                ProfessionActionPacket::handle
        );

        // 技能释放请求包（客户端 → 服务端）
        // 客户端按技能快捷键时发送，委托 skills 模块校验（冷却/资源/已学）+ 应用 + 同步
        registrar.playToServer(
                CastSkillPacket.TYPE,
                CastSkillPacket.STREAM_CODEC,
                CastSkillPacket::handle
        );

        // 怪物信息查询/回复包、HUD 开关包由 client 模块自行注册
        // 职业同步包由 profession 模块自行注册
        // 属性点点数同步包（SyncPlayerAttributePointsPacket）由 attributepoints 模块自行注册
        // 属性点配置同步包（上方）注册在此处，供 client 模块通过 core 访问客户端镜像
    }
}
