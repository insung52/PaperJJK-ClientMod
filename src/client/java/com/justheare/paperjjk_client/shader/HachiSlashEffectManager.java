package com.justheare.paperjjk_client.shader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 팔(Hachi) 격자 참격 post-processing 효과 매니저.
 *
 * 서버로부터 히트 위치(월드 좌표)를 수신하면,
 * 랜덤 회전 각도와 비틀림 각도를 생성하여 인스턴스로 저장한다.
 * GameRendererMixin 에서 매 프레임 월드→화면 UV 투영 후 렌더링.
 *
 * 애니메이션: 0.3초 (중심에서 양쪽으로 퍼지며 파바박 등장 → 소멸)
 */
public class HachiSlashEffectManager {

    /** TIME_OFFSET_MAX(0.15) + LINE_ANIM_DUR(0.20) */
    public static final float DURATION = 0.35f;

    public static class HachiSlashEffect {
        public final float worldX, worldY, worldZ;
        /** 선 집합 A 각도 (라디안, 0 ~ π) */
        public final float angle;
        /** 선 집합 B 각도 (Angle + 70~110° 사이 랜덤 비틀림) */
        public final float skewAngle;
        public final long startMs;

        public HachiSlashEffect(float wx, float wy, float wz) {
            worldX  = wx; worldY = wy; worldZ = wz;
            angle   = (float)(Math.random() * Math.PI);
            // skew: 70° ~ 110° (라디안으로 변환)
            float skewDeg = 70f + (float)(Math.random() * 40f);
            skewAngle = angle + (float)(skewDeg * Math.PI / 180.0);
            startMs = System.currentTimeMillis();
        }

        public float getTime() {
            return (System.currentTimeMillis() - startMs) / 1000.0f;
        }

        public boolean isDone() {
            return getTime() >= DURATION;
        }
    }

    private static final List<HachiSlashEffect> effects = new ArrayList<>();

    /** 서버 패킷 수신 시 호출 (클라이언트 메인 스레드) */
    public static void addEffect(float wx, float wy, float wz) {
        effects.add(new HachiSlashEffect(wx, wy, wz));
    }

    /** 만료 인스턴스 제거 후 활성 목록 반환 */
    public static List<HachiSlashEffect> getActiveEffects() {
        effects.removeIf(HachiSlashEffect::isDone);
        return Collections.unmodifiableList(effects);
    }

    public static boolean hasActiveEffects() {
        return effects.stream().anyMatch(e -> !e.isDone());
    }
}
