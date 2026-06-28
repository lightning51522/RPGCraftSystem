package com.rpgcraft.region.apply;

import com.rpgcraft.core.attribute.SimpleAttributeModifier;
import com.rpgcraft.core.event.RPGEventBus;
import com.rpgcraft.core.event.attribute.GatherAttributeEvent;
import com.rpgcraft.region.RegionManager;
import com.rpgcraft.region.data.AttributeMod;
import com.rpgcraft.region.data.RegionView;
import com.rpgcraft.region.spatial.RegionLocator;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * 非玩家实体属性注入监听器
 * <p>
 * 监听 {@link GatherAttributeEvent}（构建非玩家实体属性快照时触发），按实体位置查询
 * 所在区域，注入对应区域的属性修饰符到快照。
 * <p>
 * <h3>为何与玩家路径不同</h3>
 * 玩家的属性走持久 {@link com.rpgcraft.core.attribute.EntityAttribute} 附件，进/出区域时
 * 由 {@link RegionManager} add/remove 修饰符（避免每次快照重算）。
 * 非玩家实体的属性是<b>动态收集</b>的（每次构建快照时通过 GatherAttributeEvent 重新注入），
 * 故无需 add/remove 跟踪，按位置即时注入即可。
 * <p>
 * <h3>玩家排除</h3>
 * 玩家虽也触发 GatherAttributeEvent（理论上），但玩家属性走附件路径，此处对 Player 跳过，
 * 避免重复注入。
 *
 * @see GatherAttributeEvent
 * @see RegionLocator
 */
public final class NpcAttributeListener {

    private NpcAttributeListener() {}

    /**
     * 注册到 {@link RPGEventBus}
     * <p>
     * 由 {@link RegionMod} 在服务端启动时调用一次。
     */
    public static void register() {
        RPGEventBus.register(GatherAttributeEvent.class, event -> {
            LivingEntity entity = event.getEntity();
            // 玩家走 RegionManager 的附件路径，此处跳过
            if (entity instanceof Player) return;
            // 仅服务端有意义（客户端无区域数据），且 GatherAttributeEvent 本身只在服务端触发
            if (entity.level().isClientSide()) return;

            List<RegionView> regions = RegionLocator.regionsAt(entity);
            for (RegionView region : regions) {
                for (AttributeMod mod : region.allMods()) {
                    Identifier sourceId = RegionManager.modifierSourceId(region.getId(), mod.attr());
                    event.addModifier(mod.attr(),
                            new SimpleAttributeModifier(sourceId, mod.op(), mod.value()));
                }
            }
        });
    }
}
