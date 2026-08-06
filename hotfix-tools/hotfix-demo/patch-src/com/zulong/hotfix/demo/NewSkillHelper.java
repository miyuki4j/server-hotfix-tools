package com.zulong.hotfix.demo;

/**
 * 热更新增类：新伤害公式。
 * 注意：此类不在 hotfix-demo.jar 中，只存在于 hotfix-patch.jar，
 * 由 appendToSystemClassLoaderSearch 加入 AppClassLoader 后加载。
 */
public class NewSkillHelper {

    public static int calcDamage(int base) {
        // 新逻辑：伤害 = 基础值 * 3
        return base * 3;
    }
}
