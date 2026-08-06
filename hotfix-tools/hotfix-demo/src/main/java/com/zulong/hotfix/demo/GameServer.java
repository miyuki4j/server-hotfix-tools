package com.zulong.hotfix.demo;

/**
 * 模拟游戏服务器：主循环周期性调用 SkillModule.calcDamage。
 *
 * 热更验证方式：启动后输出 "damage = base * 2"，
 * 执行 hotfix 后输出应变为 "damage = base * 3"（且来自新类 NewSkillHelper）。
 */
public class GameServer {

    public static void main(String[] args) throws Exception {
        System.out.println("[GameServer] started, pid file check via jps");
        int tick = 0;
        while (true) {
            tick++;
            int dmg = SkillModule.calcDamage(10);
            System.out.println("[GameServer] tick=" + tick + " damage=" + dmg);
            Thread.sleep(2000);
        }
    }
}
