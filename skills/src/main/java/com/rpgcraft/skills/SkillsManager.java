package com.rpgcraft.skills;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.registry.ISkillSystem;
import com.rpgcraft.core.registry.RPGSystems;
import com.rpgcraft.core.skill.PlayerSkillData;
import com.rpgcraft.core.skill.api.ISkill;
import com.rpgcraft.skills.network.PlaySkillAnimationPacket;
import com.rpgcraft.skills.network.SyncPlayerSkillsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * 技能模块全局门面
 * <p>
 * 保留对 {@link PlayerSkillData} 附件与 {@link SkillsRegistry} 的静态引用，
 * 实现 {@link ISkillSystem}，完成主动技能 MVP 闭环。
 * <p>
 * <h3>技能资源</h3>
 * 技能释放消耗 {@code skill_point} 属性。该属性 ID 在本模块以字面量声明
 * （{@link #SKILL_POINT_ID}），与 profession/attributes 模块的本地常量引用模式一致，
 * 遵循"插件互不依赖"铁律——不依赖 attributes 模块的 {@code DefaultAttributes}。
 * <p>
 * <h3>伤害接入</h3>
 * 走 vanilla {@code target.hurt(damageSource, amount)}，由
 * {@code CombatEventHandler.onLivingDamagePre} 接管 RPG 公式。攻击类型由玩家手持武器解析
 * （{@code IAttackTypeResolver}），与普通近战一致。
 *
 * @apiNote 内部 API — 第三方模组应通过 {@link RPGSystems} 门面访问，不应直接依赖此类。
 */
public class SkillsManager {

    /**
     * 技能资源属性 ID（{@code skill_point}）
     * <p>
     * 字面量声明，与 attributes 模块的 {@code DefaultAttributes.SKILL_POINT_ID} 指向同一字面量，
     * 不引入对 attributes 模块的编译期依赖。
     */
    public static final Identifier SKILL_POINT_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "skill_point");

    private static SkillsRegistry registry;
    private static DeferredRegister<AttachmentType<?>> deferredRegister;

    /** PlayerSkillData 附件 Supplier */
    public static Supplier<AttachmentType<PlayerSkillData>> PLAYER_SKILLS;

    /**
     * 初始化技能模块
     * <p>
     * 创建注册表、注册附件类型、注册到 {@link RPGSystems} 门面。技能定义由
     * {@link SkillsDefinitionLoader} 在 datapack reload 时灌入 registry。
     */
    public static void init() {
        registry = new SkillsRegistry();

        deferredRegister = DeferredRegister.create(
                NeoForgeRegistries.ATTACHMENT_TYPES, "rpgcraftcore");

        PLAYER_SKILLS = deferredRegister.register("player_skills",
                () -> AttachmentType.builder(PlayerSkillData::new)
                        .serialize(PlayerSkillData.CODEC)
                        .build()
        );

        // 注册附件 Supplier 到 RPGSystems（供客户端 UI 访问）
        RPGSystems.registerPlayerSkillsAttachment(PLAYER_SKILLS);

        // 注册到 RPGSystems 统一门面
        RPGSystems.registerSkillSystem(new SkillSystemImpl());
    }

    public static DeferredRegister<AttachmentType<?>> getDeferredRegister() {
        return deferredRegister;
    }

    public static SkillsRegistry getRegistry() {
        return registry;
    }

    /** 读取玩家技能数据附件 */
    public static PlayerSkillData getData(ServerPlayer player) {
        return player.getData(PLAYER_SKILLS);
    }

    // ====================================================================
    // 同步
    // ====================================================================

    /**
     * 同步玩家技能数据到客户端
     */
    public static void syncToClient(ServerPlayer player) {
        PlayerSkillData data = getData(player);
        PacketDistributor.sendToPlayer(player,
                new SyncPlayerSkillsPacket(
                        new java.util.LinkedHashMap<>(data.getCooldowns()),
                        java.util.List.copyOf(data.getLearned())
                ));
    }

    // ====================================================================
    // 资源查询/扣除（私有工具）
    // ====================================================================

    /**
     * 查询玩家当前 skill_point 值
     */
    private static int getSkillPoints(ServerPlayer player) {
        AttachmentType<EntityAttribute> type = AttributeManager.getTypeById(SKILL_POINT_ID);
        if (type == null) return 0;
        return player.getData(type).getValue();
    }

    /**
     * 扣减玩家 skill_point（仿战斗扣血：直接覆盖缓存值，非负）
     *
     * @return 实际扣减后的剩余值
     */
    private static int consumeSkillPoints(ServerPlayer player, int cost) {
        AttachmentType<EntityAttribute> type = AttributeManager.getTypeById(SKILL_POINT_ID);
        if (type == null) return 0;
        EntityAttribute attr = player.getData(type);
        int newValue = Math.max(0, attr.getValue() - cost);
        attr.setValue(newValue);
        return newValue;
    }

    // ====================================================================
    // SkillSystemImpl（实现 ISkillSystem）
    // ====================================================================

    /**
     * 技能系统实现：完成 MVP 闭环
     */
    private static final class SkillSystemImpl implements ISkillSystem {

        @Override
        public void syncToClient(ServerPlayer player) {
            SkillsManager.syncToClient(player);
        }

        @Override
        public ISkill getSkillById(Identifier id) {
            return registry.getById(id);
        }

        @Override
        public Collection<ISkill> getAllSkills() {
            return registry.getAll();
        }

        @Override
        public boolean canCast(ServerPlayer player, Identifier skillId) {
            ISkill skill = registry.getById(skillId);
            if (skill == null) return false;

            PlayerSkillData data = getData(player);
            long gameTime = player.level().getGameTime();

            // 冷却未结束
            if (data.isOnCooldown(skillId, gameTime)) return false;

            // 资源不足
            if (getSkillPoints(player) < skill.getResourceCost()) return false;

            // 已学习（MVP 下视为全部已学习；学习系统预留此检查）
            if (!data.hasLearned(skillId)) {
                // MVP 兜底：未标记学习时自动学习并放行（保证默认可玩）
                // 正式学习系统接入后改为 return false
                data.learn(skillId);
            }

            return true;
        }

        @Override
        public boolean cast(ServerPlayer player, Identifier skillId) {
            ISkill skill = registry.getById(skillId);
            if (skill == null) return false;
            if (!canCast(player, skillId)) return false;

            PlayerSkillData data = getData(player);
            long gameTime = player.level().getGameTime();

            // 1. 扣资源
            consumeSkillPoints(player, skill.getResourceCost());

            // 2. 启动冷却
            data.startCooldown(skillId, gameTime + skill.getCooldownTicks());

            // 3. 发动画播放包到客户端（PAL 播放）
            SkillsMod.LOGGER.info("[技能动画] 服务端 cast 成功，发送动画包 animationId={}", skill.getAnimationId());
            PacketDistributor.sendToPlayer(player,
                    new PlaySkillAnimationPacket(skill.getAnimationId()));

            // 4. 对前方目标造成 vanilla hurt（CombatEventHandler 接管 RPG 公式）
            applyDamageToTargets(player, skill);

            // 5. 同步附件（冷却刷新供 HUD 显示）
            syncToClient(player);

            return true;
        }

        @Override
        public boolean isOnCooldown(ServerPlayer player, Identifier skillId) {
            return getData(player).isOnCooldown(skillId, player.level().getGameTime());
        }

        @Override
        public long getRemainingCooldown(ServerPlayer player, Identifier skillId) {
            return getData(player).getRemaining(skillId, player.level().getGameTime());
        }

        @Override
        public void resetAllCooldowns(ServerPlayer player) {
            getData(player).clearCooldowns();
            syncToClient(player);
        }

        // ====================================================================
        // 目标检索 + 伤害应用
        // ====================================================================

        /**
         * 对玩家前方 AABB 内的 LivingEntity 造成 vanilla hurt
         * <p>
         * MVP 简化：检索玩家前方圆锥范围（AABB），排除玩家自身，对每个目标
         * 调 {@code target.hurt(playerAttack(player), damage)}。伤害值由
         * {@code CombatEventHandler} 接管走 RPG 公式（含暴击/防御/事件）。
         *
         * @param caster 施法者
         * @param skill  技能定义
         */
        private void applyDamageToTargets(ServerPlayer caster, ISkill skill) {
            double range = skill.getRange();
            AABB box = AABB.ofSize(caster.getEyePosition(), range * 2, range * 2, range * 2)
                    .move(caster.getLookAngle().scale(range / 2));

            List<LivingEntity> targets = caster.level().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != caster && e.isAlive() && caster.hasLineOfSight(e));

            if (targets.isEmpty()) return;

            float damage = skill.getDamageAmount();
            for (LivingEntity target : targets) {
                // 走 vanilla hurt：CombatEventHandler.onLivingDamagePre 接管 RPG 公式
                // 攻击类型由玩家手持武器解析（IAttackTypeResolver），与普通近战一致
                target.hurt(caster.damageSources().playerAttack(caster), damage);
            }
        }
    }
}
