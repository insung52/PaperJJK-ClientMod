# Post-Processing 아키텍처

## 핵심 원칙

**효과 타입당 별도 JSON + 셰이더 파일, 같은 타입 여러 인스턴스는 배열로 묶어 단일 pass 처리**

---

## 파일 구조

```
src/main/resources/assets/paperjjk-client/
├── post_effect/
│   ├── refraction.json       ← 왜곡 + bloom (AO/AKA/MURASAKI)
│   ├── domain.json           ← 영역 효과 (간이영역, 무량공처 등)
│   └── (future).json         ← 새 효과 추가 시 새 파일
└── shaders/post/
    ├── refraction.fsh
    ├── domain.fsh
    └── (future).fsh
```

---

## 렌더링 구조

### Injection Point
`GameRendererMixin` → `GameRenderer.render()` 내부, **`InGameHud.render()` 직전**
- 월드는 렌더링 완료, HUD는 아직 미렌더링 → HUD가 왜곡되지 않음
- `JJKDepthCache`는 이미 채워진 상태 (WorldRendererMixin이 선행 실행)

### 실행 순서
```
GameRenderer.render()
  └─ renderWorld()               ← 월드 렌더링
       └─ [WorldRendererMixin]   ← JJKDepthCache에 depth 캡처
  └─ clearDepthTexture()         ← GUI용 depth 클리어
  └─ [GameRendererMixin inject]  ← 우리 post-processing 실행
       ├─ refractionProcessor.render()   (활성 시)
       ├─ domainProcessor.render()       (활성 시)
       └─ futureProcessor.render()       (활성 시)
  └─ InGameHud.render()          ← HUD (왜곡 없음)
```

### Pass 수 = 활성 효과 타입 수 (인스턴스 수 아님)
```java
// 같은 타입 인스턴스 5개 → 셰이더 내 배열로 처리, pass는 1번만
domainProcessor.render(mainFb, ...);    // 1 pass
refractionProcessor.render(mainFb, ...); // 1 pass
```

---

## 성능

- **GPU**: 단순 fullscreen 셰이더 1개 ≈ 0.1~0.5ms (1080p 기준)
- **실제 병목**: `FrameGraphBuilder` CPU 오버헤드 → 타입별 1회 실행으로 최소화
- **10개 타입 동시 활성**: GPU ~5ms, 16ms 프레임 예산 안에 충분

---

## 각 효과 파일 설계 원칙

- uniform 구조는 해당 효과에 최적화 (다른 효과의 uniform과 무관하게 설계)
- 같은 타입 여러 인스턴스: `uniform block` 배열 또는 구조체 배열로 묶음
- std140 레이아웃 사용

---

## 현재 구현된 효과

### refraction (왜곡 + bloom)
- **파일**: `refraction.json`, `refraction.fsh`
- **uniform**: `RefractionConfig` (std140, 32 bytes)
  - `vec2 EffectCenter` — 스크린 좌표 (0~1)
  - `float EffectRadius` — 스크린 반경 (원근 보정됨)
  - `float EffectStrength` — 왜곡 세기
  - `int EffectType` — 0=AO(파랑), 1=AKA(빨강), 2=MURASAKI(보라)
  - `float EffectDepth` — depth buffer값 (occlusion용)
  - `float Time` — 애니메이션 시간
  - `float EffectWorldDist` — 카메라-효과 거리(블록), 왜곡 감쇠용
- **특징**: depth occlusion(블록에 가려짐), 원근 보정, bloom + 나선 애니메이션
- **Java 관리**: `RefractionEffectManager`, `GameRendererMixin`

---

## 새 효과 추가 체크리스트

1. `post_effect/(이름).json` 생성
2. `shaders/post/(이름).fsh` 생성
3. 필요한 uniform 설계 (std140)
4. `GameRendererMixin`에 새 processor 로드 + render 호출 추가
5. 서버 패킷 → 클라이언트 매니저 연동 (필요 시)
