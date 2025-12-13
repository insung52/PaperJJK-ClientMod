package com.justheare.paperjjk_client.network.packets;

import net.minecraft.network.PacketByteBuf;

/**
 * CE_UPDATE (0x04) - Server → Client
 * 주술력 정보 업데이트 패킷
 */
public class CEUpdatePacket {
    private final int currentCE;
    private final int maxCE;
    private final float regenRate;
    private final String technique;
    private final boolean blocked;

    public CEUpdatePacket(int currentCE, int maxCE, float regenRate, String technique, boolean blocked) {
        this.currentCE = currentCE;
        this.maxCE = maxCE;
        this.regenRate = regenRate;
        this.technique = technique;
        this.blocked = blocked;
    }

    public static CEUpdatePacket read(PacketByteBuf buf) {
        int currentCE = buf.readInt();
        int maxCE = buf.readInt();
        float regenRate = buf.readFloat();
        String technique = buf.readString();
        boolean blocked = buf.readBoolean();
        return new CEUpdatePacket(currentCE, maxCE, regenRate, technique, blocked);
    }

    public void write(PacketByteBuf buf) {
        buf.writeInt(currentCE);
        buf.writeInt(maxCE);
        buf.writeFloat(regenRate);
        buf.writeString(technique);
        buf.writeBoolean(blocked);
    }

    // Getters
    public int getCurrentCE() { return currentCE; }
    public int getMaxCE() { return maxCE; }
    public float getRegenRate() { return regenRate; }
    public String getTechnique() { return technique; }
    public boolean isBlocked() { return blocked; }
}
