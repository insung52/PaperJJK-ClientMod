package com.justheare.paperjjk_client.network.packets;

import net.minecraft.network.PacketByteBuf;

/**
 * TECHNIQUE_USE (0x02) - Server → Client
 * 술식 사용 결과 패킷
 */
public class TechniqueUsePacket {
    private final boolean success;
    private final int techniqueId;
    private final byte reason;
    private final String message;

    public TechniqueUsePacket(boolean success, int techniqueId, byte reason, String message) {
        this.success = success;
        this.techniqueId = techniqueId;
        this.reason = reason;
        this.message = message;
    }

    public static TechniqueUsePacket read(PacketByteBuf buf) {
        boolean success = buf.readBoolean();
        int techniqueId = buf.readInt();
        byte reason = buf.readByte();
        String message = buf.readString();
        return new TechniqueUsePacket(success, techniqueId, reason, message);
    }

    public void write(PacketByteBuf buf) {
        buf.writeBoolean(success);
        buf.writeInt(techniqueId);
        buf.writeByte(reason);
        buf.writeString(message);
    }

    // Getters
    public boolean isSuccess() { return success; }
    public int getTechniqueId() { return techniqueId; }
    public byte getReason() { return reason; }
    public String getMessage() { return message; }
}
