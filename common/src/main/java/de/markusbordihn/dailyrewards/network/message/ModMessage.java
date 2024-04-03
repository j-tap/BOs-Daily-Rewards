package de.markusbordihn.dailyrewards.network.message;

import de.markusbordihn.dailyrewards.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.thread.BlockableEventLoop;

public abstract class ModMessage<T extends PacketListener> implements Packet<T> {

  public abstract void onReceive(T listener);

  public abstract void read(FriendlyByteBuf buffer);

  @Override
  public void handle(T listener) {
    BlockableEventLoop<?> engine =
        listener instanceof ServerGamePacketListenerImpl handler
            ? handler.player.getServer()
            : null;
    if (engine == null) {
      NetworkHandler.log.error("Failed to handle packet {}, engine {} is unknown", this, listener);
      return;
    }

    if (engine.isSameThread()) {
      this.onReceive(listener);
    } else {
      engine.executeIfPossible(
          () -> {
            if (listener.isAcceptingMessages()) {
              try {
                this.handle(listener);
              } catch (Exception exception) {
                if (listener.shouldPropagateHandlingExceptions()) {
                  throw exception;
                }
                NetworkHandler.log.error(
                    "Failed to handle packet {}, suppressing error", this, exception);
              }
            } else {
              NetworkHandler.log.debug("Ignoring packet due to disconnection: {}", this);
            }
          });
    }
  }
}
