package com.justheare.paperjjk_client;

import com.justheare.paperjjk_client.command.DebugCommand;
import com.justheare.paperjjk_client.command.SkillConfigCommand;
import com.justheare.paperjjk_client.data.ClientGameData;
import com.justheare.paperjjk_client.keybind.JJKKeyBinds;
import com.justheare.paperjjk_client.network.ClientPacketHandler;
import com.justheare.paperjjk_client.render.DebugRenderer;
// import com.justheare.paperjjk_client.render.DomainRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.PacketByteBuf;
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

		// 1. Payload 타입 등록
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
			// JJKPostProcessor.getInstance().cleanup();
			// DomainRenderer.dispose();
		});

		// WorldRenderEvents를 사용한 렌더링
		// AFTER_ENTITIES: 엔티티 렌더링 후, 반투명 지형 전에 실행
		WorldRenderEvents.AFTER_ENTITIES.register((WorldRenderContext context) -> {
			// Debug cube rendering
			Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
			DebugRenderer.render(context.matrices(), camera, context.consumers());

			// Domain rendering (disabled for now)
			// float tickDelta = context.tickCounter().getTickDelta(true);
			// DomainRenderer.render(context.matrices(), tickDelta, camera);
		});


		// Post-processing은 이제 GameRendererMixin에서 처리됩니다 (Iris처럼 renderLevel의 TAIL에 injection)

		// 클라이언트 틱 이벤트: 도메인 반지름 업데이트
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientGameData.updateAllDomains();
		});
	}
}
