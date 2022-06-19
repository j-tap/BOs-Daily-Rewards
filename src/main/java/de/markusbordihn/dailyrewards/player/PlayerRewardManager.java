/**
 * Copyright 2022 Markus Bordihn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package de.markusbordihn.dailyrewards.player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.server.ServerLifecycleHooks;

import de.markusbordihn.dailyrewards.Constants;
import de.markusbordihn.dailyrewards.config.CommonConfig;
import de.markusbordihn.dailyrewards.data.RewardData;
import de.markusbordihn.dailyrewards.data.RewardUserData;
import de.markusbordihn.dailyrewards.network.NetworkHandler;

@EventBusSubscriber
public class PlayerRewardManager {

  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);

  private static final CommonConfig.Config COMMON = CommonConfig.COMMON;

  private static final short REWARD_CHECK_TICK = 20 * 60; // every 1 Minute

  private static final MutableComponent claimCommand = Component.literal("/DailyRewards claim")
      .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withClickEvent(
          new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/DailyRewards claim")));

  private static int rewardTimePerDayTicks = 30 * 60 * 20;
  private static short ticker = 0;
  private static Set<ServerPlayer> playerList = ConcurrentHashMap.newKeySet();

  protected PlayerRewardManager() {}

  @SubscribeEvent
  public static void onServerAboutToStartEvent(ServerAboutToStartEvent event) {
    playerList = ConcurrentHashMap.newKeySet();
    rewardTimePerDayTicks = COMMON.rewardTimePerDay.get() * 60 * 20;

    log.info("Daily rewards will be granted after {} min ({} ticks) a player is online.",
        COMMON.rewardTimePerDay.get(), rewardTimePerDayTicks);
  }

  @SubscribeEvent
  public static void handlePlayerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) {
    String username = event.getPlayer().getName().getString();
    if (username.isEmpty()) {
      return;
    }
    ServerPlayer player =
        ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayerByName(username);
    log.debug("{} Player {} {} logged in.", Constants.LOG_NAME, username, player);

    // Sync data and add Player to reward.
    NetworkHandler.syncGeneralRewardForCurrentMonth(player);
    NetworkHandler.syncUserRewardForCurrentMonth(player);
    playerList.add(player);
  }

  @SubscribeEvent
  public static void handlePlayerLoggedOutEvent(PlayerEvent.PlayerLoggedOutEvent event) {
    String username = event.getPlayer().getName().getString();
    if (username.isEmpty()) {
      return;
    }
    ServerPlayer player =
        ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayerByName(username);
    log.debug("{} Player {} {} logged out.", Constants.LOG_NAME, username, player);
    playerList.remove(player);
  }

  @SubscribeEvent
  public static void handleServerTickEvent(TickEvent.ServerTickEvent event) {
    if (event.phase == TickEvent.Phase.END || ticker++ < REWARD_CHECK_TICK
        || playerList.isEmpty()) {
      return;
    }
    for (ServerPlayer player : playerList) {
      if (player.tickCount > rewardTimePerDayTicks) {
        UUID uuid = player.getUUID();
        RewardUserData rewardUserData = RewardUserData.get();
        if (!rewardUserData.hasRewardedToday(uuid)) {
          // Update stored data
          rewardUserData.setLastRewardedDayForCurrentMonth(uuid);
          int rewardedDays = rewardUserData.increaseRewardedDaysForCurrentMonth(uuid);

          // Add reward for rewarded Days.
          ItemStack itemStack = RewardData.get().getRewardForCurrentMonth(rewardedDays);
          if (itemStack.isEmpty()) {
            log.error("Reward {} for day {} for current month was empty!", itemStack, rewardedDays);
          } else {
            rewardUserData.addRewardForCurrentMonth(rewardedDays, uuid, itemStack);
            player.sendSystemMessage(Component.translatable(Constants.TEXT_PREFIX + "rewarded_item",
                player.getName(), itemStack, rewardedDays));
            player.sendSystemMessage(
                Component.translatable(Constants.TEXT_PREFIX + "claim_rewards", claimCommand));
            NetworkHandler.syncUserRewardForCurrentMonth(player);
          }

          log.info("Reward player {} daily reward for {} days with {} ...", player, rewardedDays,
              itemStack);
        }
      }
    }
    ticker = 0;
  }

}
