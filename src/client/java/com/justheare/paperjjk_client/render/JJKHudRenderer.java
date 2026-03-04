package com.justheare.paperjjk_client.render;

import com.justheare.paperjjk_client.data.ClientGameData;
import com.justheare.paperjjk_client.data.PlayerData;
import com.justheare.paperjjk_client.network.PacketIds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * PaperJJK 커스텀 HUD 렌더러.
 * HudRenderCallback.EVENT에서 매 프레임 호출.
 *
 * 레이아웃:
 *   좌하단 — 스킬 슬롯 4칸 (X/C/V/B)
 *   우하단 — 술식명 · 신체강화 바 · 주력 바
 */
public class JJKHudRenderer {

    // ── 치수 ──────────────────────────────────────────────────────────────
    private static final int SLOT_SIZE  = 36;   // 슬롯 박스 한 변
    private static final int SLOT_GAP   = 4;    // 슬롯 간격
    private static final int BAR_W      = 150;  // CE·신체강화 바 가로
    private static final int BAR_H      = 10;   // 바 세로
    private static final int MARGIN     = 8;    // 화면 가장자리 여백
    private static final int HOTBAR_H   = 22;   // 바닐라 핫바 높이

    // ── 색상 (ARGB) ───────────────────────────────────────────────────────
    private static final int BG             = 0xAA111111; // 배경 (반투명)
    private static final int BORDER         = 0xFF666666; // 슬롯 테두리 (기본)
    private static final int BORDER_ACTIVE  = 0xFF4499FF; // 슬롯 테두리 (발동 중 — 파란)
    private static final int BORDER_CHARGE  = 0xFFFFCC22; // 슬롯 테두리 (충전·재충전 — 노란)
    private static final int SLOT_HAS_SKILL = 0xFF1A2B44; // 스킬 있는 슬롯 배경 (어두운 파랑)
    private static final int SLOT_RUNNING   = 0xFF1A3566; // 스킬 발동 중 배경 (밝은 파랑)
    private static final int SLOT_EMPTY     = 0xFF1A1A1A; // 빈 슬롯 배경
    private static final int GAUGE_WHITE    = 0x66FFFFFF; // 충전·재충전 게이지 (흰색)
    private static final int GAUGE_DIM      = 0x22FFFFFF; // 발동 중 게이지 (연한 흰색)
    private static final int LOCK_BG        = 0xFFFFCC22; // 자물쇠 배경 (노란)
    private static final int LOCK_TEXT      = 0xFF111111; // 자물쇠 텍스트 (검정)
    private static final int LABEL_COLOR    = 0xFFCCCCCC; // 레이블 텍스트 (연회색)
    private static final int DISABLED_OVL   = 0xAA000000; // 비활성화 오버레이
    private static final int CE_NORMAL      = 0xFF4488FF; // 주력 바 (정상)
    private static final int CE_LOW         = 0xFFFF6622; // 주력 바 (30% 이하)
    private static final int CE_BLOCKED     = 0xFF555555; // 주력 바 (술식 차단)
    private static final int BODY_BAR       = 0xFFFF8833; // 신체강화 바 (주황)
    private static final int TEXT_WHITE     = 0xFFFFFFFF;
    private static final int TEXT_GREY      = 0xFF888888;
    private static final int TEXT_SEMI      = 0xAAFFFFFF; // 반투명 흰색 (술식명)

    public static void render(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!com.justheare.paperjjk_client.data.PlayerData.isHudEnabled()) return;

        int W = client.getWindow().getScaledWidth();
        int H = client.getWindow().getScaledHeight();
        int base = H - HOTBAR_H; // 핫바 위 기준선

        renderSkillSlots(ctx, client, base);
        renderRightPanel(ctx, client, W, base);
    }

    // ── 좌하단: 스킬 슬롯 (X / C / V / B) ────────────────────────────────

    private static void renderSkillSlots(DrawContext ctx, MinecraftClient client, int base) {
        String[] keys  = { "X", "C", "V", "B" };
        String[] names = {
            PlayerData.getSlot1Skill(),
            PlayerData.getSlot2Skill(),
            PlayerData.getSlot3Skill(),
            PlayerData.getSlot4Skill()
        };

        // 레이아웃 계산 (아래에서 위로) — 핫바에 딱 붙임
        int keyH    = 8;  // 단축키 텍스트 높이
        int keyGap  = 2;  // 슬롯과 키 레이블 사이
        int keyY    = base - 1 - keyH;               // 단축키 텍스트 top (핫바 1px 위)
        int slotBot = keyY - keyGap;                 // 슬롯 박스 bottom
        int slotTop = slotBot - SLOT_SIZE;           // 슬롯 박스 top
        int infoGap = 2;                             // 슬롯 위 정보 영역과의 간격
        int infoH   = 7;                             // 자물쇠/레이블 높이 (작은 텍스트)
        int infoY   = slotTop - infoGap - infoH;    // 정보 영역 top

        for (int i = 0; i < 4; i++) {
            int x = MARGIN + i * (SLOT_SIZE + SLOT_GAP);
            String name = names[i];
            boolean hasSkill  = name != null && !name.isEmpty();
            float gauge       = ClientGameData.getSlotGauge((byte)(i + 1));
            byte  state       = ClientGameData.getSlotState((byte)(i + 1));
            boolean disabled  = ClientGameData.isSlotDisabled((byte)(i + 1));
            boolean locked    = ClientGameData.isSlotLocked((byte)(i + 1));
            String  label     = ClientGameData.getSlotLabel((byte)(i + 1));

            boolean isCharging   = state == PacketIds.SlotGaugeState.CHARGING;
            boolean isActive     = state == PacketIds.SlotGaugeState.ACTIVE;
            boolean isRecharging = state == PacketIds.SlotGaugeState.RECHARGING;
            boolean isChargingAny = isCharging || isRecharging; // 충전 or 재충전

            // 테두리: 발동 중=파란, 충전·재충전=노란, 기본=회색
            int borderColor = isActive ? BORDER_ACTIVE : isChargingAny ? BORDER_CHARGE : BORDER;
            ctx.fill(x - 1, slotTop - 1, x + SLOT_SIZE + 1, slotBot + 1, borderColor);

            // 배경: 발동 중=밝은 파랑, 스킬 있음=어두운 파랑, 없음=어두운 회색
            int bgColor = isActive ? SLOT_RUNNING : hasSkill ? SLOT_HAS_SKILL : SLOT_EMPTY;
            ctx.fill(x, slotTop, x + SLOT_SIZE, slotBot, bgColor);

            // 게이지 (아래에서 위로 fill)
            // 충전·재충전: 흰색 (서버 power 비율)
            // 발동 중: 연한 흰색 (chargedOutput 비율, 스킬별 override 가능)
            if (gauge > 0f) {
                int gaugeH     = (int)(SLOT_SIZE * Math.min(1f, gauge));
                int gaugeColor = isActive ? GAUGE_DIM : GAUGE_WHITE;
                ctx.fill(x, slotBot - gaugeH, x + SLOT_SIZE, slotBot, gaugeColor);
            }

            // 비활성화 오버레이
            if (disabled) {
                ctx.fill(x, slotTop, x + SLOT_SIZE, slotBot, DISABLED_OVL);
            }

            // 스킬명 (가운데 정렬, 언더스코어 뒤 부분만 표시)
            if (hasSkill) {
                String display = shortName(name);
                int tx = x + (SLOT_SIZE - client.textRenderer.getWidth(display)) / 2;
                int ty = slotTop + (SLOT_SIZE - 8) / 2;
                ctx.drawText(client.textRenderer, display, tx, ty,
                        disabled ? TEXT_GREY : TEXT_WHITE, true);
            }

            // ── 슬롯 위 정보 영역: 자물쇠 + 레이블 ─────────────────────
            // 자물쇠 아이콘 (locked=true일 때): 노란 배경 사각형 + "L" 텍스트
            // 레이블 (label 비어있지 않을 때): 거리/방향 텍스트
            boolean hasLabel  = label != null && !label.isEmpty();
            if (locked || hasLabel) {
                // 자물쇠: 8×7 노란 사각형, 왼쪽에 배치
                int curX = x;
                if (locked) {
                    ctx.fill(curX, infoY, curX + 8, infoY + infoH, LOCK_BG);
                    ctx.drawText(client.textRenderer, "L", curX + 1, infoY, LOCK_TEXT, false);
                    curX += 10; // 자물쇠 너비(8) + 간격(2)
                }
                // 레이블 텍스트 (자물쇠 없으면 슬롯 왼쪽부터, 있으면 그 옆부터)
                if (hasLabel) {
                    ctx.drawText(client.textRenderer, label, curX, infoY, LABEL_COLOR, false);
                }
            }

            // 단축키 레이블 (슬롯 아래, 가운데 정렬, 회색)
            int kx = x + (SLOT_SIZE - client.textRenderer.getWidth(keys[i])) / 2;
            ctx.drawText(client.textRenderer, keys[i], kx, keyY, TEXT_GREY, false);
        }
    }

    /**
     * 스킬 ID에서 표시용 짧은 이름 추출.
     * "infinity_ao" → "ao", "infinity_passive" → "passive"
     * 언더스코어 없으면 앞 6글자 표시.
     */
    private static String shortName(String skillId) {
        int idx = skillId.lastIndexOf('_');
        String s = idx >= 0 ? skillId.substring(idx + 1) : skillId;
        return s.length() > 6 ? s.substring(0, 6) : s;
    }

    // ── 우하단: 술식명 · 신체강화 바 · 주력 바 ───────────────────────────

    private static void renderRightPanel(DrawContext ctx, MinecraftClient client, int W, int base) {
        // 서버에서 CE 데이터를 한 번도 받지 않았으면 표시 안 함
        if (ClientGameData.getMaxCE() <= 0) return;

        int barX = W - BAR_W - MARGIN;

        // CE 바: 핫바 바로 위
        int ceY    = base - MARGIN - BAR_H;
        // 신체강화 바: CE 바 위
        int bodyY  = ceY - BAR_H - 4;
        // 술식명: 신체강화 바 위
        int techY  = bodyY - 10;

        // ── 술식명 ──────────────────────────────────────────────────────
        String tech = ClientGameData.getCurrentTechnique();
        if (tech != null && !tech.isEmpty()) {
            int tx = barX + (BAR_W - client.textRenderer.getWidth(tech)) / 2;
            ctx.drawText(client.textRenderer, tech, tx, techY, TEXT_SEMI, true);
        }

        // ── 신체강화 바 (주황) ───────────────────────────────────────────
        float bodyRatio = ClientGameData.getBodyReinforcementRatio();
        ctx.fill(barX - 1, bodyY - 1, barX + BAR_W + 1, bodyY + BAR_H + 1, BG);
        if (bodyRatio > 0f) {
            int fillW = (int)(BAR_W * Math.min(1f, bodyRatio));
            ctx.fill(barX, bodyY, barX + fillW, bodyY + BAR_H, BODY_BAR);
        }

        // ── 주력 바 (CE) ─────────────────────────────────────────────────
        float cePercent = ClientGameData.getCEPercentage();
        boolean blocked = ClientGameData.isBlocked();
        int curCE = ClientGameData.getCurrentCE();
        int maxCE = ClientGameData.getMaxCE();

        ctx.fill(barX - 1, ceY - 1, barX + BAR_W + 1, ceY + BAR_H + 1, BG);

        int ceFillW = (int)(BAR_W * Math.max(0f, Math.min(1f, cePercent)));
        int ceColor = blocked ? CE_BLOCKED : cePercent < 0.3f ? CE_LOW : CE_NORMAL;
        if (ceFillW > 0) {
            ctx.fill(barX, ceY, barX + ceFillW, ceY + BAR_H, ceColor);
        }

        // CE 수치 텍스트 (바 아래 가운데)
        String ceText = curCE + " / " + maxCE;
        int tx = barX + (BAR_W - client.textRenderer.getWidth(ceText)) / 2;
        ctx.drawText(client.textRenderer, ceText, tx, ceY + BAR_H + 2, TEXT_WHITE, true);
    }
}
