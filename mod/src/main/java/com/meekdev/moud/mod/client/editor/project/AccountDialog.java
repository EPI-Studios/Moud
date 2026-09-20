package com.meekdev.moud.mod.client.editor.project;

import com.meekdev.moud.mod.client.account.ForumLink;
import com.meekdev.moud.mod.client.account.ForumLink.Code;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class AccountDialog {

    private static final String POPUP_ID = "##account";
    private static final float WIDTH = 520.0f;
    private static final float PADDING = 28.0f;
    private static final float WHO_HEIGHT = 64.0f;
    private static final float WHO_MARK = 38.0f;
    private static final float CODE_HEIGHT = 74.0f;
    private static final float COPY_WIDTH = 92.0f;
    private static final float BUTTON_WIDTH = 124.0f;
    private static final float WIDE_BUTTON_WIDTH = 150.0f;
    private static final float CORNER = 6.0f;
    private static final float CORNER_MARK = 10.0f;
    private static final float CORNER_BOX = 12.0f;
    private static final long COPIED_MILLIS = 1600L;

    private final IconWidgets icons;
    private volatile @Nullable CompletableFuture<Code> running;
    private volatile @Nullable Code code;
    private volatile @Nullable String problem;
    private boolean openRequested;
    private long copiedAt;

    public AccountDialog(IconWidgets icons) {
        this.icons = icons;
    }

    public void open() {
        openRequested = true;
    }

    public String playing() {
        Minecraft client = Minecraft.getInstance();
        String name = client.getUser() == null ? null : client.getUser().getName();
        return name == null || name.isBlank() ? "Not signed in" : name;
    }

    public void render() {
        if (openRequested) {
            ImGui.openPopup(POPUP_ID);
            openRequested = false;
        }
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getCenterX(), viewport.getCenterY(), ImGuiCond.Appearing, 0.5f, 0.5f);
        ImGui.setNextWindowSize(EditorScale.of(WIDTH), 0.0f, ImGuiCond.Always);
        float padding = EditorScale.of(PADDING);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, padding, padding);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, EditorScale.of(CORNER_BOX));
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, HubStyle.hairline());
        ImGui.pushStyleColor(ImGuiCol.PopupBg, HubStyle.SURFACE);
        ImGui.pushStyleColor(ImGuiCol.Border, HubStyle.LINE_STRONG);
        boolean open = ImGui.beginPopupModal(POPUP_ID, ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoSavedSettings | ImGuiWindowFlags.AlwaysAutoResize);
        ImGui.popStyleColor(2);
        ImGui.popStyleVar(3);
        if (!open) return;
        renderContents();
        ImGui.endPopup();
    }

    private void renderContents() {
        float width = ImGui.getContentRegionAvailX();
        HubStyle.write(HubStyle.HEADING, true, HubStyle.TEXT_BRIGHT, "Your account");
        ImGui.dummy(0.0f, EditorScale.of(8.0f));
        HubStyle.write(HubStyle.SMALL, false, HubStyle.TEXT_MUTED,
                "Link this Minecraft account to the forum, so your posts carry your name and your skin.");
        ImGui.dummy(0.0f, EditorScale.of(22.0f));
        renderWho(width);
        ImGui.dummy(0.0f, EditorScale.of(18.0f));
        Code ready = code;
        if (ready != null) renderCode(ready, width);
        else renderWaiting();
        ImGui.dummy(0.0f, EditorScale.of(18.0f));
        renderButtons(width, ready != null);
    }

    private void renderWho(float width) {
        float height = EditorScale.of(WHO_HEIGHT);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImDrawList draw = ImGui.getWindowDrawList();
        float rounding = EditorScale.of(CORNER);
        draw.addRectFilled(x, y, x + width, y + height, HubStyle.BACKGROUND, rounding);
        draw.addRect(x, y, x + width, y + height, HubStyle.LINE_STRONG, rounding, 0, HubStyle.hairline());
        float mark = EditorScale.of(WHO_MARK);
        float markX = x + EditorScale.of(13.0f);
        float markY = y + (height - mark) * 0.5f;
        draw.addRectFilled(markX, markY, markX + mark, markY + mark, HubStyle.SURFACE_HOVER, EditorScale.of(CORNER_MARK));
        float glyph = mark * 0.56f;
        draw.addImage(icons.imageId(HubIcon.USER_CIRCLE.resourcePath()),
                markX + (mark - glyph) * 0.5f, markY + (mark - glyph) * 0.5f,
                markX + (mark + glyph) * 0.5f, markY + (mark + glyph) * 0.5f,
                0.0f, 0.0f, 1.0f, 1.0f, HubStyle.TEXT_MUTED);
        float textX = markX + mark + EditorScale.of(14.0f);
        HubStyle.paintTracked(draw, HubStyle.CAPTION, textX, y + EditorScale.of(14.0f), HubStyle.TEXT_LIGHT, "PLAYING AS");
        HubStyle.paint(draw, HubStyle.TILE_TITLE, true, textX, y + EditorScale.of(14.0f + HubStyle.CAPTION + 7.0f),
                HubStyle.TEXT_BRIGHT, playing());
        ImGui.dummy(width, height);
    }

    private void renderWaiting() {
        if (running != null && !running.isDone()) {
            HubStyle.write(HubStyle.SMALL, false, HubStyle.TEXT_MUTED, "Asking Mojang to confirm this account...");
            return;
        }
        String failure = problem;
        if (failure != null) {
            HubStyle.write(HubStyle.SMALL, false, HubStyle.TEXT_MAIN, failure);
            return;
        }
        HubStyle.write(HubStyle.SMALL, false, HubStyle.TEXT_MUTED,
                "Moud asks Mojang to confirm who you are, then hands you a code to type on the site.");
    }

    private void renderCode(Code ready, float width) {
        float height = EditorScale.of(CODE_HEIGHT);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImDrawList draw = ImGui.getWindowDrawList();
        float rounding = EditorScale.of(CORNER);
        draw.addRectFilled(x, y, x + width, y + height, HubStyle.BACKGROUND, rounding);
        draw.addRect(x, y, x + width, y + height, HubStyle.ACCENT, rounding, 0, HubStyle.hairline());
        float copyWidth = EditorScale.of(COPY_WIDTH);
        float room = width - copyWidth - EditorScale.of(26.0f);
        float textWidth = HubStyle.trackedWidth(HubStyle.TITLE, ready.code(), HubStyle.CODE_TRACKING);
        HubStyle.paintTracked(draw, HubStyle.TITLE, x + EditorScale.of(14.0f) + Math.max(0.0f, (room - textWidth) * 0.5f),
                HubStyle.middle(y, height, HubStyle.TITLE), HubStyle.TEXT_BRIGHT, ready.code(), HubStyle.CODE_TRACKING);
        ImGui.setCursorScreenPos(x + width - copyWidth - EditorScale.of(12.0f),
                y + (height - EditorScale.of(HubStyle.ACTION_HEIGHT)) * 0.5f);
        boolean fresh = System.currentTimeMillis() - copiedAt < COPIED_MILLIS;
        if (HubStyle.action("account-copy", fresh ? "Copied" : "Copy", copyWidth, false, true)) {
            Minecraft.getInstance().keyboardHandler.setClipboard(ready.code());
            copiedAt = System.currentTimeMillis();
        }
        ImGui.setCursorScreenPos(x, y + height);
        ImGui.dummy(width, 0.0f);
        ImGui.dummy(0.0f, EditorScale.of(12.0f));
        HubStyle.write(HubStyle.SMALL, false, HubStyle.TEXT_MUTED, "Type it on " + ForumLink.site());
        ImGui.dummy(0.0f, EditorScale.of(4.0f));
        HubStyle.write(HubStyle.SMALL, false, HubStyle.TEXT_LIGHT,
                "It lasts " + Math.max(1, ready.expiresIn() / 60) + " minutes and works once.");
    }

    private void renderButtons(float width, boolean hasCode) {
        boolean busy = running != null && !running.isDone();
        String label = hasCode ? "Get another code" : "Get a code";
        float mainWidth = EditorScale.of(hasCode ? WIDE_BUTTON_WIDTH : BUTTON_WIDTH);
        float closeWidth = EditorScale.of(BUTTON_WIDTH);
        float gap = EditorScale.of(8.0f);
        ImGui.setCursorPosX(ImGui.getCursorPosX() + width - mainWidth - closeWidth - gap);
        if (HubStyle.action("account-close", "Close", closeWidth, false, true)) ImGui.closeCurrentPopup();
        ImGui.sameLine(0.0f, gap);
        if (HubStyle.action("account-start", label, mainWidth, true, !busy)) begin();
    }

    private void begin() {
        problem = null;
        code = null;
        copiedAt = 0L;
        CompletableFuture<Code> attempt = ForumLink.start();
        running = attempt;
        attempt.whenComplete((made, failure) -> {
            if (failure != null) {
                Throwable why = failure.getCause() == null ? failure : failure.getCause();
                problem = why.getMessage() == null ? "That did not work." : why.getMessage();
                return;
            }
            code = made;
        });
    }
}
