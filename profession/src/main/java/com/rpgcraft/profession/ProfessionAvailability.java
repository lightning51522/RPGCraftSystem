package com.rpgcraft.profession;

import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.profession.api.IProfessionRegistry;
import net.minecraft.resources.Identifier;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 职业有效启用状态计算（显式开关 + 前置级联）
 * <p>
 * 一个职业「真正加载」当且仅当：
 * <ol>
 *   <li>它被显式启用（见 {@link ProfessionLoadConfig#isExplicitlyEnabled}，缺省 true）；<b>且</b></li>
 *   <li>它<b>所有直接前置职业</b>都同样「有效启用」（递归）。</li>
 * </ol>
 * 即：当前置职业被关闭时，其后继职业会被<b>级联关闭</b>，即使它本身在 JSON 里标为 true。
 * <p>
 * 例如 {@code berserker}（前置 {@code warrior}）：关闭 {@code warrior} 会使
 * {@code berserker} 也被关闭；复合职业 {@code witchblade}（前置 berserker + mage）任一被关都会被关。
 * <p>
 * 实现采用记忆化 + 环检测：用 {@link IdentityHashMap} 缓存单次遍历结果，
 * 每次配置变更后由 {@link ProfessionLoadConfig#applyConfig} 调用 {@link #invalidateCache()} 清空。
 * 计算整体加锁以保证缓存一致性。
 * <p>
 * 找不到职业实例（未注册）或检测到前置环时，按<b>安全默认 = 禁用</b>处理并告警。
 */
public final class ProfessionAvailability {

    private ProfessionAvailability() {
    }

    /**
     * 计算结果的缓存（职业实例 → 有效启用）。注意生命周期与配置加载对齐，reload 后清空。
     * 用 IdentityHashMap 以职业实例身份为键（注册表内同一 ID 永远是同一实例）。
     */
    private static final Map<IProfession, Boolean> cache = new IdentityHashMap<>();

    /**
     * 查询某职业 ID 是否「有效启用」。
     * <p>
     * 未注册的 ID 视为禁用（防御）。
     *
     * @param id 职业标识符
     * @return true 表示该职业应出现在职业树中、可被进阶与升级
     */
    public static boolean isEffectiveEnabled(Identifier id) {
        IProfessionRegistry registry = ProfessionManager.getRegistry();
        if (registry == null) {
            // 注册中心尚未初始化 —— 视为全部不可用（极端早期，一般不会到这里）
            return false;
        }
        IProfession prof = registry.getProfession(id);
        if (prof == null) return false;
        return isEffectiveEnabled(prof, registry);
    }

    /**
     * 查询某职业实例是否「有效启用」（给定注册表，便于测试/复用）。
     * <p>
     * 这是 {@link #isEffectiveEnabled(Identifier)} 的实例版入口：调用方已持有职业实例与注册表时
     * 可直接调用，避免二次查找；同时方便单元测试在不依赖 {@link ProfessionManager} 静态状态的情况下
     * 验证级联逻辑。
     *
     * @param prof     职业实例
     * @param registry 职业注册表（用于解析前置）
     * @return true 表示该职业有效启用
     */
    public static boolean isEffectiveEnabled(IProfession prof, IProfessionRegistry registry) {
        return isEffectiveEnabled(prof, registry, new IdentityHashMap<>());
    }

    /**
     * 递归计算（带缓存 + 环检测）。
     *
     * @param prof       当前职业
     * @param registry   注册表（用于解析前置）
     * @param visiting   本次调用链上的「正在计算中」标记，用于环检测
     */
    private static boolean isEffectiveEnabled(IProfession prof, IProfessionRegistry registry,
                                              Map<IProfession, Boolean> visiting) {
        synchronized (cache) {
            Boolean cached = cache.get(prof);
            if (cached != null) return cached;
        }
        // 环检测：若该职业已在本条计算链上，说明前置图存在环 —— 安全默认禁用并告警
        if (visiting.put(prof, Boolean.TRUE) != null) {
            ProfessionMod.LOGGER.warn("职业 {} 的前置依赖存在环，已按禁用处理", prof.getId());
            return false;
        }
        boolean result;
        if (!ProfessionLoadConfig.isExplicitlyEnabled(prof.getId())) {
            result = false;
        } else {
            result = true;
            for (Identifier prereqId : prof.getPrerequisites()) {
                IProfession prereq = registry.getProfession(prereqId);
                if (prereq == null) {
                    // 前置实例缺失 —— 视为本职业不可用（防御错误配置）
                    result = false;
                    break;
                }
                if (!isEffectiveEnabled(prereq, registry, visiting)) {
                    result = false;
                    break;
                }
            }
        }
        visiting.remove(prof);
        synchronized (cache) {
            cache.put(prof, result);
        }
        return result;
    }

    /**
     * 清空计算缓存。配置 reload 后由 {@link ProfessionLoadConfig} 调用。
     */
    public static void invalidateCache() {
        synchronized (cache) {
            cache.clear();
        }
    }
}
