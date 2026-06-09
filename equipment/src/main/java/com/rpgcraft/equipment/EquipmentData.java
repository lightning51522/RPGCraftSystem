package com.rpgcraft.equipment;

import com.rpgcraft.core.equipment.EquipmentBonus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 装备模块的附件注册入口
 * <p>
 * 管理装备加成追踪附件（{@link #EQUIPMENT_BONUS}）的注册。
 * 与属性模块的 {@link com.rpgcraft.core.attribute.AttributeManager} 分离，
 * 避免装备逻辑混入属性包。
 * <p>
 * <b>追踪附件的生命周期：</b>
 * <ol>
 *   <li><b>创建时机：</b>首次通过 {@code player.getData()} 访问时自动创建空 {@link LinkedHashMap}</li>
 *   <li><b>填充时机：</b>
 *     <ul>
 *       <li>玩家登录时，由 {@code onPlayerLogin} 调用 {@code restoreBonusTracking()} 从当前装备计算并填充</li>
 *       <li>玩家重生后，由 {@code onPlayerClone} 调用 {@code restoreBonusTracking()} 重新填充</li>
 *       <li>装备变化时，由 {@link EquipmentEventHandler} 触发 {@code onEquipmentChange()} 更新</li>
 *     </ul>
 *   </li>
 *   <li><b>不序列化：</b>附件数据不写入存档，每次加载世界后由登录事件重新计算，
 *       确保追踪数据始终与当前装备配置文件一致（即使配置被 /reload 修改）</li>
 * </ol>
 */
public class EquipmentData {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "rpgcraftcore");

    /**
     * 装备加成追踪附件：{@code key=属性ID字符串}, {@code value=当前已应用的加成}
     * <p>
     * 存储玩家身上当前生效的装备加成快照，用于在装备变化时计算差值（新旧差分）。
     * 使用 {@link LinkedHashMap} 保持插入顺序，确保序列化和遍历的确定性。
     * <p>
     * <b>为什么用字符串作 key 而不是 Identifier？</b>
     * 因为 AttachmentType 的序列化机制会将 Map 直接写为 NBT，
     * 而 Identifier 没有原生的 NBT Codec 支持，字符串形式 {@code "namespace:path"} 更简单可靠。
     */
    public static final Supplier<AttachmentType<Map<String, EquipmentBonus>>> EQUIPMENT_BONUS =
            ATTACHMENTS.register(
                    "equipment_bonus",
                    () -> AttachmentType.builder(
                                    (java.util.function.Supplier<Map<String, EquipmentBonus>>) LinkedHashMap::new
                            )
                            .build()
            );

    /**
     * 获取底层 DeferredRegister，用于注册到 Mod 事件总线
     * <p>
     * 与 {@link com.rpgcraft.core.attribute.AttributeManager#getDeferredRegister()} 模式对齐。
     */
    public static DeferredRegister<AttachmentType<?>> getAttachmentRegister() {
        return ATTACHMENTS;
    }
}
