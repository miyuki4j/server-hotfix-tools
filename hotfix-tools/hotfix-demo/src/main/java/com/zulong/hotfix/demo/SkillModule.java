package com.zulong.hotfix.demo;

/**
 * 旧逻辑（线上版本）：伤害 = 基础值 * 2。
 * 热更后将被 redefine 为调用新类 NewSkillHelper 的逻辑。
 */
public class SkillModule {

    public static int calcDamage(int base) {
        return base * 2;
    }
}
