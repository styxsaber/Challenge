/* OneHeart UHC + 1 Inventory Slot mit Config - Fabric Mod */
package com.example.oneheart;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.GameRules;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Properties;

public class OneHeartMod implements ModInitializer {
    private static double MAX_HEALTH = 2.0D; // Standard: 1 Herz
    private static int ALLOWED_SLOT_INDEX = 0; // Standard: Hotbar-Slot 0

    private static final String CONFIG_FILE = "config/oneheart-uhc.properties";

    @Override
    public void onInitialize() {
        loadConfig();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            enforceMaxHealth(player);
            enforceInventorySingleSlot(player);
        });

        ServerTickEvents.START_SERVER_TICK.register(this::onServerTick);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                enforceMaxHealth(player);
                enforceInventorySingleSlot(player);
            }
        });
    }

    private void loadConfig() {
        try {
            File file = new File(CONFIG_FILE);
            Properties props = new Properties();
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    props.load(reader);
                }
            } else {
                file.getParentFile().mkdirs();
            }
            MAX_HEALTH = Double.parseDouble(props.getProperty("max_health", "2.0"));
            ALLOWED_SLOT_INDEX = Integer.parseInt(props.getProperty("allowed_slot_index", "0"));

            props.setProperty("max_health", String.valueOf(MAX_HEALTH));
            props.setProperty("allowed_slot_index", String.valueOf(ALLOWED_SLOT_INDEX));
            try (FileWriter writer = new FileWriter(file)) {
                props.store(writer, "OneHeart UHC Config");
            }
        } catch (Exception e) {
            System.err.println("[OneHeartMod] Konnte Config nicht laden, benutze Standardwerte.");
        }
    }

    private void onServerTick(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            try {
                GameRules.BooleanRule rule = (GameRules.BooleanRule) world.getGameRules().get(GameRules.NATURAL_REGENERATION);
                if (rule != null && rule.get()) {
                    world.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false, server);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void enforceMaxHealth(ServerPlayerEntity player) {
        try {
            EntityAttributeInstance inst = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (inst != null) {
                if (inst.getBaseValue() != MAX_HEALTH) {
                    inst.setBaseValue(MAX_HEALTH);
                }
                if (player.getHealth() > MAX_HEALTH) {
                    player.setHealth((float) MAX_HEALTH);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void enforceInventorySingleSlot(ServerPlayerEntity player) {
        try {
            DefaultedList<ItemStack> inv = player.getInventory().main;
            if (ALLOWED_SLOT_INDEX < 0 || ALLOWED_SLOT_INDEX >= inv.size()) return;
            ItemStack allowed = inv.get(ALLOWED_SLOT_INDEX);

            for (int i = 0; i < inv.size(); i++) {
                if (i == ALLOWED_SLOT_INDEX) continue;
                ItemStack stack = inv.get(i);
                if (stack == null || stack.isEmpty()) continue;

                if (allowed == null || allowed.isEmpty()) {
                    inv.set(ALLOWED_SLOT_INDEX, stack.copy());
                    inv.set(i, ItemStack.EMPTY);
                    allowed = inv.get(ALLOWED_SLOT_INDEX);
                } else {
                    if (ItemStack.canCombine(allowed, stack)) {
                        int allowedSpace = allowed.getMaxCount() - allowed.getCount();
                        int transfer = Math.min(allowedSpace, stack.getCount());
                        if (transfer > 0) {
                            allowed.increment(transfer);
                            stack.decrement(transfer);
                            if (stack.isEmpty()) inv.set(i, ItemStack.EMPTY);
                        }
                    }
                    if (!stack.isEmpty()) {
                        player.dropItem(stack.copy(), true);
                        inv.set(i, ItemStack.EMPTY);
                    }
                }
            }

            for (int armorIndex = 0; armorIndex < player.getInventory().armor.size(); armorIndex++) {
                ItemStack armor = player.getInventory().armor.get(armorIndex);
                if (armor != null && !armor.isEmpty()) {
                    if (inv.get(ALLOWED_SLOT_INDEX).isEmpty()) {
                        inv.set(ALLOWED_SLOT_INDEX, armor.copy());
                    } else {
                        player.dropItem(armor.copy(), true);
                    }
                    player.getInventory().armor.set(armorIndex, ItemStack.EMPTY);
                }
            }

            ItemStack offhand = player.getInventory().offHand.get(0);
            if (offhand != null && !offhand.isEmpty()) {
                if (inv.get(ALLOWED_SLOT_INDEX).isEmpty()) {
                    inv.set(ALLOWED_SLOT_INDEX, offhand.copy());
                } else {
                    player.dropItem(offhand.copy(), true);
                }
                player.getInventory().offHand.set(0, ItemStack.EMPTY);
            }

        } catch (Exception ignored) {
        }
    }
}