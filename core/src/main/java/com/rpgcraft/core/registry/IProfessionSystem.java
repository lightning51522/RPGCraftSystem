package com.rpgcraft.core.registry;

import com.rpgcraft.core.profession.api.IProfession;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * 职业系统接口
 * <p>
 * 由职业模块注册实现，提供职业查询、切换、同步等能力。
 * 供其他模块（如命令系统、客户端渲染、快照恢复、登录同步）通过 {@link RPGSystems} 访问。
 *
 * @see RPGSystems#registerProfessionSystem(IProfessionSystem)
 * @see RPGSystems#getProfessionSystem()
 */
public interface IProfessionSystem {

    /**
     * 同步职业数据到客户端
     *
     * @param player 服务端玩家
     */
    void syncToClient(ServerPlayer player);

    /**
     * 获取玩家的当前职业
     *
     * @param player 服务端玩家
     * @return 当前职业实例
     */
    IProfession getProfession(ServerPlayer player);

    /**
     * 根据 ID 查询职业
     * <p>
     * 供客户端渲染和命令系统使用。
     *
     * @param id 职业标识符
     * @return 职业实例，不存在则返回 {@code null}
     */
    IProfession getProfessionById(Identifier id);

    /**
     * 获取所有已注册的职业
     *
     * @return 不可变职业集合
     */
    Collection<IProfession> getAllProfessions();

    /**
     * 设置玩家的职业
     * <p>
     * 自动处理属性加成的移除和应用，并同步到客户端。
     *
     * @param player       服务端玩家
     * @param professionId 新职业标识符
     */
    void setProfession(ServerPlayer player, Identifier professionId);
}
