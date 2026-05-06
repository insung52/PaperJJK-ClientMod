package com.justheare.paperjjk_client;

import com.justheare.paperjjk_client.command.DebugCommand;
import com.justheare.paperjjk_client.command.PlayerInfoCommand;
import com.justheare.paperjjk_client.command.SkillConfigCommand;
import com.justheare.paperjjk_client.data.ClientGameData;
import com.justheare.paperjjk_client.keybind.JJKKeyBinds;
import com.justheare.paperjjk_client.network.ClientPacketHandler;
import com.justheare.paperjjk_client.particle.DomainParticle;
import com.justheare.paperjjk_client.particle.ModParticles;
import com.justheare.paperjjk_client.render.DebugRenderer;
import com.justheare.paperjjk_client.render.JJKHudRenderer;
// import com.justheare.paperjjk_client.render.DomainRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.network.codec.PacketCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PaperJJK Client Mod - 클라이언트 엔트리포인트
 *
 * Paper 플러그인과 통신하여:
 * - 정확한 키보드 입력 감지 (R, F, G, V, Z, X, C)
 * - 커스텀 HUD (주술력 게이지, 술식 정보)
 * - 비주얼 효과 (도메인, 파티클, 셰이더)
 */
public class PaperJJKClientClient implements ClientModInitializer {
	public static final String MOD_ID = "paperjjk-client";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final int PROTOCOL_VERSION = 1;

	@Override
	public void onInitializeClient() {
		LOGGER.info("========================================");
		LOGGER.info("  PaperJJK Client Mod 초기화 시작");
		LOGGER.info("  버전: 1.0.0 | 프로토콜: {}", PROTOCOL_VERSION);
		LOGGER.info("========================================");

		// 1. 커스텀 파티클 타입 등록 (ParticleFactoryRegistry보다 먼저 해야 함)
		LOGGER.info("[0/5] 커스텀 파티클 등록 중...");
		ModParticles.register();
		ParticleFactoryRegistry.getInstance().register(ModParticles.DOMAIN_FRAGMENT, DomainParticle.Factory::new);

		// 2. Payload 타입 등록
		LOGGER.info("[1/5] Payload 타입 등록 중...");
		PayloadTypeRegistry.playC2S().register(
			JJKKeyBinds.JJKPayload.ID,
			PacketCodec.of(
				(value, buf) -> buf.writeBytes(value.data()),
				buf -> {
					byte[] data = new byte[buf.readableBytes()];
					buf.readBytes(data);
					return new JJKKeyBinds.JJKPayload(data);
				}
			)
		);

		// 2. 패킷 핸들러 등록
		LOGGER.info("[2/5] 패킷 핸들러 등록 중...");
		ClientPacketHandler.register();

		// 3. 키바인드 등록
		LOGGER.info("[3/5] 키바인드 등록 중...");
		JJKKeyBinds.register();

		// 4. 명령어 등록
		LOGGER.info("[4/5] 명령어 등록 중...");
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			PlayerInfoCommand.register(dispatcher);
			SkillConfigCommand.register(dispatcher);
			DebugCommand.register(dispatcher);
		});

		// 5. 이벤트 리스너 등록
		LOGGER.info("[5/5] 이벤트 리스너 등록 중...");
		registerEventListeners();

		// 6. Post-processing (향후 구현 예정)
		// LOGGER.info("[6/6] Post-processing 셰이더 초기화 중...");
		// JJKPostProcessor.getInstance().init();

		LOGGER.info("========================================");
		LOGGER.info("  PaperJJK Client Mod 초기화 완료!");
		LOGGER.info("========================================");
	}

	/**
	 * 클라이언트 이벤트 리스너 등록
	 */
	private void registerEventListeners() {
		// 서버 접속 시
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			LOGGER.info("서버 접속: 데이터 초기화");
			ClientGameData.reset();
		});

		// 서버 나갈 때
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			LOGGER.info("서버 연결 해제: 데이터 정리");
			ClientGameData.reset();
			JJKKeyBinds.reset();
			com.justheare.paperjjk_client.shader.AmbientKaiSlashManager.clearAll();
			com.justheare.paperjjk_client.shader.MizushiChargeEffectManager.stop();
			// JJKPostProcessor.getInstance().cleanup();
			// DomainRenderer.dispose();
		});

		// 구체 렌더링은 GameRendererMixin.paperjjk$renderSpheres()에서 처리.
		// WorldRenderer.render() 직후 inject하여 Iris g-buffer 우회.


		// Post-processing은 이제 GameRendererMixin에서 처리됩니다 (Iris처럼 renderLevel의 TAIL에 injection)

		// HUD 렌더러 등록
		HudRenderCallback.EVENT.register((drawContext, tickCounter) ->
				JJKHudRenderer.render(drawContext));

		// 클라이언트 틱 이벤트: 도메인 반지름 업데이트 + 파티클 테스트
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientGameData.updateAllDomains();
			com.justheare.paperjjk_client.shader.PassiveBarrierManager.tick(client);

			// /jjkdebug particle 활성화 시 플레이어 주변에 파티클 스폰
			if (DebugCommand.particleTestActive && client.world != null && client.player != null) {
				double px = client.player.getX();
				double py = client.player.getY() + 1.0;
				double pz = client.player.getZ();
				net.minecraft.util.math.random.Random rng = client.player.getRandom();

				// 틱마다 2개 스폰, 랜덤 방향으로 약간의 수평 속도
				for (int i = 0; i < 2; i++) {
					double vx = (rng.nextDouble() - 0.5) * 0.12;
					double vz = (rng.nextDouble() - 0.5) * 0.12;
					client.particleManager.addParticle(
						ModParticles.DOMAIN_FRAGMENT,
						px + (rng.nextDouble() - 0.5) * 0.5,
						py,
						pz + (rng.nextDouble() - 0.5) * 0.5,
						vx, 0.0, vz
					);
				}
			}
		});
	}
}
