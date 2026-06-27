package com.rpgcraft.region;

import com.rpgcraft.region.data.EnvironmentType;
import com.rpgcraft.region.data.Region;
import com.rpgcraft.region.data.RegionDraft;
import com.rpgcraft.region.data.RegionPolygon;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区域草稿注册表（内存态，不持久化）
 * <p>
 * 管理 {@code setregion init} 创建的草稿，按 NAME 索引。草稿在 {@code setregion done}
 * 后转为正式 {@link Region} 并移除；服务器重启时草稿丢失（仅正式区域持久化）。
 * <p>
 * <h3>命名空间</h3>
 * 草稿 NAME 与正式区域 NAME 共享命名空间：init 时校验 NAME 未被草稿或正式区域占用，
 * 避免重名冲突。
 * <p>
 * <h3>线程安全</h3>
 * 命令在主线程执行，理论无需并发；但用 {@link ConcurrentHashMap} 防御性保证安全。
 *
 * @see RegionDraft
 */
public final class RegionDraftManager {

    /** 运行时区域的 ID 命名空间（区分于 datapack 的 rpgcraftcore） */
    public static final String RUNTIME_NAMESPACE = "rpgcraftregion";

    /** 草稿表：NAME → RegionDraft */
    private static final Map<String, RegionDraft> drafts = new ConcurrentHashMap<>();

    private RegionDraftManager() {}

    /** 查询草稿（可能为 null） */
    public static RegionDraft get(String name) {
        return drafts.get(name);
    }

    /** 是否存在指定草稿 */
    public static boolean exists(String name) {
        return drafts.containsKey(name);
    }

    /**
     * 初始化草稿
     *
     * @param name      草稿名
     * @param envType   环境类型（init 时校验已注册）
     * @param dimension 维度
     * @param centerX   中心 X（整数）
     * @param centerZ   中心 Z（整数）
     * @param size      正方形边长（≥1）
     * @return 新建的草稿
     * @throws IllegalStateException NAME 已被草稿占用
     */
    public static RegionDraft initDraft(String name, EnvironmentType envType,
                                         ResourceKey<Level> dimension,
                                         int centerX, int centerZ, int size) {
        if (drafts.containsKey(name)) {
            throw new IllegalStateException("草稿 " + name + " 已存在");
        }
        RegionDraft draft = new RegionDraft(name, envType.id(), dimension, centerX, centerZ, size);
        drafts.put(name, draft);
        return draft;
    }

    /**
     * 定稿：将草稿转为正式区域
     * <p>
     * 校验 envTypeId 与草稿 init 时一致，用环境类型效果套用草稿几何生成 {@link Region}，
     * 然后从草稿表移除该草稿。
     *
     * @param name     草稿名
     * @param envType  环境类型（done 时校验与 init 一致）
     * @return 生成的正式区域
     * @throws IllegalArgumentException 草稿不存在 / envType 与草稿不一致
     */
    public static Region finalize(String name, EnvironmentType envType) {
        RegionDraft draft = drafts.get(name);
        if (draft == null) {
            throw new IllegalArgumentException("草稿 " + name + " 不存在");
        }
        if (!draft.getEnvTypeId().equals(envType.id())) {
            throw new IllegalArgumentException(
                    "环境类型不匹配：草稿 init 时为 " + draft.getEnvTypeId()
                            + "，done 时传入 " + envType.id());
        }
        RegionPolygon polygon = draft.getCurrentPolygon();
        if (polygon == null || polygon.vertexCount() < 3) {
            throw new IllegalArgumentException("草稿 " + name + " 几何无效（顶点 < 3）");
        }
        // 生成运行时区域 ID（命名空间 rpgcraftregion，path = name）
        Identifier regionId = Identifier.fromNamespaceAndPath(RUNTIME_NAMESPACE, name);
        Region region = envType.createRegion(regionId, name, draft.getDimension(), polygon);
        drafts.remove(name);
        return region;
    }

    /** 移除草稿（不生成区域） */
    public static void remove(String name) {
        drafts.remove(name);
    }

    /** 清空所有草稿（服务器停止时） */
    public static void clear() {
        drafts.clear();
    }

    /**
     * 生成运行时区域的 ID（命名空间 rpgcraftregion）
     * <p>
     * 供 {@link RuntimeRegionSavedData} / {@link RegionsRegistry} 等统一构造运行时区域 ID。
     */
    public static Identifier runtimeRegionId(String name) {
        return Identifier.fromNamespaceAndPath(RUNTIME_NAMESPACE, name);
    }
}
