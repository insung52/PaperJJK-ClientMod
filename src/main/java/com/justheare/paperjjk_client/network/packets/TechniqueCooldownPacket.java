package com.justheare.paperjjk_client.network.packets;

import net.minecraft.network.PacketByteBuf;

/**
 * TECHNIQUE_COOLDOWN (0x05) - Server → Client
 * 쿨다운 정보 패킷
 */
public class TechniqueCooldownPacket {
    private final byte techniqueSlot;
    private final int cooldownTicks;
    private final int maxCooldown;

    public TechniqueCooldownPacket(byte techniqueSlot, int cooldownTicks, int maxCooldown) {
        this.techniqueSlot = techniqueSlot;
        this.cooldownTicks = cooldownTicks;
        this.maxCooldown = maxCooldown;
    }

    public static TechniqueCooldownPacket read(PacketByteBuf buf) {
        byte techniqueSlot = buf.readByte();
        int cooldownTicks = buf.readInt();
        int maxCooldown = buf.readInt();
        return new TechniqueCooldownPacket(techniqueSlot, cooldownTicks, maxCooldown);
    }

    public void write(PacketByteBuf buf) {
        buf.writeByte(techniqueSlot);
        buf.writeInt(cooldownTicks);
        buf.writeInt(maxCooldown);
    }

    // Getters
    public byte getTechniqueSlot() { return techniqueSlot; }
    public int getCooldownTicks() { return cooldownTicks; }
    public int getMaxCooldown() { return maxCooldown; }

    public float getCooldownPercentage() {
        if (maxCooldown == 0) return 0;
        return (float) cooldownTicks / maxCooldown;
    }
}
