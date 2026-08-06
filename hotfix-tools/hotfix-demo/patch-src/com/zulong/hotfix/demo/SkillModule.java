package com.zulong.hotfix.demo;

/**
 * 热更后的 SkillModule：只改方法体（不加方法、不加字段、不改签名），
 * 方法体内直接调用新类 NewSkillHelper 的静态方法。
 */
public class SkillModule {

    public static int calcDamage(int base) {
        return NewSkillHelper.calcDamage(base);
    }
}
