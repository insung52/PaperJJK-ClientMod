package com.justheare.paperjjk_client;

import com.justheare.paperjjk_client.command.SkillConfigCommand;
import com.justheare.paperjjk_client.data.ClientGameData;
import com.justheare.paperjjk_client.keybind.JJKKeyBinds;
import com.justheare.paperjjk_client.network.ClientPacketHandler;
import com.justheare.paperjjk_client.render.DomainRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
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
		});

		// 5. 이벤트 리스너 등록
		LOGGER.info("[5/5] 이벤트 리스너 등록 중...");
		registerEventListeners();

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
		});

		// 렌더링 이벤트: HUD 렌더링 시 도메인 구체 렌더링
		HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
			// Create a new MatrixStack for world rendering
			net.minecraft.client.util.math.MatrixStack matrices = new net.minecraft.client.util.math.MatrixStack();
			// tickDelta is not needed for our rendering
			DomainRenderer.render(matrices, 1.0f);
		});

		// 클라이언트 틱 이벤트: 도메인 반지름 업데이트
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientGameData.updateAllDomains();
		});
	}
}
