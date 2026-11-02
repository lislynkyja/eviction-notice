package com.evictionnotice.eviction;

import com.evictionnotice.config.EvictionConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.TraderLlama;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;

/**
 * Defines which mobs have to stay out of zones. Villagers are always affected, the others depend
 * on the config. Used by the pathfinding mixin and the sweep.
 */
public final class EvictionSubjects {

    private EvictionSubjects() {
    }

    public static boolean isSubject(Entity entity, EvictionConfig cfg) {
        EvictionConfig.Mobs mobs = cfg.mobs;
        if (entity instanceof IronGolem) {
            return mobs.ironGolems;
        }
        if (entity instanceof Villager) {
            return true;
        }
        if (entity instanceof ZombieVillager) {
            return mobs.zombieVillagers;
        }
        if (entity instanceof WanderingTrader) {
            return mobs.wanderingTraders.enabled;
        }
        if (entity instanceof TraderLlama) {
            return mobs.wanderingTraders.enabled && mobs.wanderingTraders.llamas;
        }
        return false;
    }
}
