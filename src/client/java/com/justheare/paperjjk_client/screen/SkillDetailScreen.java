package com.justheare.paperjjk_client.screen;

import com.justheare.paperjjk_client.data.PlayerData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skill Detail Screen - Shows skill description
 */
public class SkillDetailScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("PaperJJK-SkillDetail");

    private final Screen parent;
    private final String skillId;
    private ButtonWidget backButton;

    public SkillDetailScreen(Screen parent, String skillId) {
        super(Text.literal("스킬 정보"));
        this.parent = parent;
        this.skillId = skillId;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 4;

        // Back button
        this.backButton = ButtonWidget.builder(
            Text.literal("뒤로"),
            button -> this.close()
        ).dimensions(centerX - 50, startY + 200, 100, 20).build();
        this.addDrawableChild(backButton);

        LOGGER.info("[Skill Detail] Screen initialized for skill: {}", skillId);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw background
        context.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);

        // Render widgets
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int startY = this.height / 4;

        PlayerData.SkillInfo skillInfo = PlayerData.getSkillInfo(skillId);

        if (skillInfo == null) {
            // Loading or not found
            String loadingText = "로딩 중...";
            context.drawText(
                this.textRenderer,
                Text.literal(loadingText),
                centerX - this.textRenderer.getWidth(loadingText) / 2,
                startY + 50,
                0xFFFFFFFF,
                true
            );
            return;
        }

        // Title (display name)
        String title = "§6" + skillInfo.displayName;
        context.drawText(
            this.textRenderer,
            Text.literal(title),
            centerX - this.textRenderer.getWidth(title) / 2,
            startY - 10,
            0xFFFFFFFF,
            true
        );

        // Skill ID
        String idText = "§7(" + skillInfo.skillId + ")";
        context.drawText(
            this.textRenderer,
            Text.literal(idText),
            centerX - this.textRenderer.getWidth(idText) / 2,
            startY + 10,
            0xFFFFFFFF,
            true
        );

        // Description
        String descLabel = "§e설명:";
        context.drawText(
            this.textRenderer,
            Text.literal(descLabel),
            centerX - 120,
            startY + 40,
            0xFFFFFFFF,
            true
        );

        // Wrap description text
        wrapText(context, skillInfo.description, centerX - 120, startY + 55, 240);

        // Required CE (now as text from server)
        String ceText = "§b주력 소모량: §f" + skillInfo.requiredCE;
        context.drawText(
            this.textRenderer,
            Text.literal(ceText),
            centerX - 120,
            startY + 130,
            0xFFFFFFFF,
            true
        );
    }

    /**
     * Wrap text to fit within width, supporting \n for manual line breaks
     */
    private void wrapText(DrawContext context, String text, int x, int y, int maxWidth) {
        // First split by manual line breaks (\n)
        String[] lines = text.split("\\n");
        int currentY = y;

        for (String paragraph : lines) {
            // Then word-wrap each line
            String[] words = paragraph.split(" ");
            StringBuilder line = new StringBuilder();

            for (String word : words) {
                String testLine = line.length() == 0 ? word : line + " " + word;

                if (this.textRenderer.getWidth(testLine) > maxWidth) {
                    // Draw current line and start new one
                    context.drawText(
                        this.textRenderer,
                        Text.literal(line.toString()),
                        x,
                        currentY,
                        0xFFFFFFFF,
                        true
                    );
                    line = new StringBuilder(word);
                    currentY += 12; // Line height
                } else {
                    line = new StringBuilder(testLine);
                }
            }

            // Draw last line of paragraph
            if (line.length() > 0) {
                context.drawText(
                    this.textRenderer,
                    Text.literal(line.toString()),
                    x,
                    currentY,
                    0xFFFFFFFF,
                    true
                );
            }

            // Move to next line for manual line break
            currentY += 12;
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    /**
     * Refresh screen (called when skill info is received from server)
     */
    public void refresh() {
        // No need to reinitialize, just triggers re-render
    }

    public String getSkillId() {
        return skillId;
    }
}
