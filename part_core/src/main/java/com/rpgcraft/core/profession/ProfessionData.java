package com.rpgcraft.core.profession;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * 玩家职业附件数据 —— 持久化玩家的职业选择
 * <p>
 * 包含主职业标识符和预留的副职业标识符。
 * 通过 {@link ProfessionManager#PLAYER_PROFESSION} 附件注册到玩家实体。
 * <p>
 * 序列化使用 {@link #CODEC}，副职业字段为 optional（当前始终为 null）。
 */
public class ProfessionData {

    /** 主职业标识符，默认为平民 */
    private Identifier professionId;

    /** 副职业标识符（预留），null 表示无副职业 */
    private Identifier secondaryProfessionId;

    /**
     * 默认构造函数 —— 职业为平民，无副职业
     */
    public ProfessionData() {
        this.professionId = ProfessionManager.COMMONER_ID;
        this.secondaryProfessionId = null;
    }

    /**
     * 全参数构造函数
     *
     * @param professionId          主职业标识符
     * @param secondaryProfessionId 副职业标识符，可为 null
     */
    public ProfessionData(Identifier professionId, Identifier secondaryProfessionId) {
        this.professionId = professionId;
        this.secondaryProfessionId = secondaryProfessionId;
    }

    /** 序列化 Codec，secondary 字段为 optional（缺失时视为 null） */
    public static final MapCodec<ProfessionData> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Identifier.CODEC.fieldOf("profession").forGetter(ProfessionData::getProfessionId),
                    Identifier.CODEC.optionalFieldOf("secondary").forGetter(d ->
                            Optional.ofNullable(d.secondaryProfessionId))
            ).apply(instance, (prof, secondary) ->
                    new ProfessionData(prof, secondary.orElse(null)))
    );

    public Identifier getProfessionId() {
        return professionId;
    }

    public void setProfessionId(Identifier professionId) {
        this.professionId = professionId;
    }

    public Identifier getSecondaryProfessionId() {
        return secondaryProfessionId;
    }

    public void setSecondaryProfessionId(Identifier secondaryProfessionId) {
        this.secondaryProfessionId = secondaryProfessionId;
    }
}
