# JJK Effects Shaderpack

PaperJJK 모드용 Iris 셰이더팩 - 굴절 및 블룸 효과

## 설치 방법

1. **Iris 설치 확인**
   - Iris Shaders가 설치되어 있어야 합니다
   - 이미 설치되어 있다면 다음 단계로

2. **셰이더팩 설치**
   ```
   이 폴더 전체(jjk_effects)를 복사:
   .minecraft/shaderpacks/
   ```

3. **셰이더팩 활성화**
   - 게임 실행
   - 비디오 설정 > Shaders
   - "jjk_effects" 선택

## 기능

### Effect 1 (파란색)
- 위치: (0, 150, 0)
- 명령어: `/jjkdebug effect 1`
- 효과:
  - 중력 렌즈 굴절 (화면 왜곡)
  - 파란색 블룸 (은은한 빛)
  - Fresnel 메시 (테두리가 밝게)

### Effect 2 (노란색/빨간색)
- 위치: (10, 150, 10)
- 명령어: `/jjkdebug effect 2`
- 효과:
  - 강력한 굴절 효과
  - **빨간색 블룸** (강렬한 빛)
  - Fresnel 메시 (작은 구)

## 현재 상태

**작동하는 것:**
- ✅ Fresnel 기반 발광 구 (모드 렌더링)
- ✅ 화면 굴절 효과 (셰이더)
- ✅ 블룸/발광 효과 (셰이더)

**제한 사항:**
- ⚠️ 효과 위치가 하드코딩됨 (테스트용)
- ⚠️ 화면 공간 위치 고정 (월드 좌표 변환 미구현)

## 다음 단계

모드에서 Iris로 데이터 전달하여 동적 효과 위치 구현:
```java
// TODO: Iris Custom Uniforms 사용
IrisApi.getInstance().setCustomUniform("jjk_effect1_pos", worldPos);
```

## 커스터마이징

`shaders/shaders.properties`에서 조절 가능:
- `JJK_EFFECT_STRENGTH`: 굴절 강도 (0.01 ~ 0.1)
- `JJK_BLOOM_STRENGTH`: 블룸 강도 (0.1 ~ 1.0)

## 트러블슈팅

**셰이더가 작동하지 않으면:**
1. Iris가 설치되어 있는지 확인
2. Iris 버전이 1.21.10과 호환되는지 확인
3. 게임 로그에서 셰이더 오류 확인

**효과가 보이지 않으면:**
1. `/jjkdebug effect 1` 또는 `2` 실행
2. 해당 위치로 이동 (F3로 좌표 확인)
3. Fresnel 메시가 보이는지 확인 (셰이더 없이도 보임)
