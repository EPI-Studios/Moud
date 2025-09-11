package com.moud.client.ui.component;

import com.moud.client.api.service.UIService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.graalvm.polyglot.Value;
import org.lwjgl.glfw.GLFW;

public class UIInput extends UIComponent {
    private String value = "";
    private final String placeholder;
    private int cursorPosition = 0;
    private int selectionStart = 0;
    private int selectionEnd = 0;
    private long lastBlinkTime = 0;
    private boolean showCursor = true;

    public UIInput(String placeholder, UIService service) {
        super("input", service, 0, 0, 200, 20, Text.literal(""));
        this.placeholder = placeholder;
        setBackgroundColor("#FFFFFF");
        setBorder(1, "#CCCCCC");
        setTextAlign("left");
        setPadding(2, 6, 2, 6);
    }

    @Override
    protected void renderText(DrawContext context) {
        String displayText = getDisplayText();
        if (displayText.isEmpty()) return;

        int textCol = parseColor(value.isEmpty() && !isFocused() ? "#999999" : textColor, opacity);
        int textX = getX() + (int) paddingLeft;
        int textY = getY() + (getHeight() - MinecraftClient.getInstance().textRenderer.fontHeight) / 2;

        if (hasSelection()) {
            renderSelection(context, displayText, textX, textY);
        }

        context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal(displayText), textX, textY, textCol, false);

        if (isFocused() && shouldShowCursor()) {
            renderCursor(context, displayText, textX, textY);
        }
    }

    private String getDisplayText() {
        if (!value.isEmpty()) return value;
        if (isFocused()) return "";
        return placeholder;
    }

    private boolean shouldShowCursor() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBlinkTime > 530) {
            showCursor = !showCursor;
            lastBlinkTime = currentTime;
        }
        return showCursor;
    }

    private void renderCursor(DrawContext context, String text, int textX, int textY) {
        String textBeforeCursor = text.substring(0, Math.min(cursorPosition, text.length()));
        int cursorX = textX + MinecraftClient.getInstance().textRenderer.getWidth(textBeforeCursor);
        int cursorColor = parseColor(textColor, opacity);

        context.fill(cursorX, textY - 1, cursorX + 1, textY + MinecraftClient.getInstance().textRenderer.fontHeight + 1, cursorColor);
    }

    private boolean hasSelection() {
        return selectionStart != selectionEnd;
    }

    private void renderSelection(DrawContext context, String text, int textX, int textY) {
        if (!hasSelection()) return;

        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);

        String beforeSelection = text.substring(0, start);
        String selection = text.substring(start, end);

        int selectionX = textX + MinecraftClient.getInstance().textRenderer.getWidth(beforeSelection);
        int selectionWidth = MinecraftClient.getInstance().textRenderer.getWidth(selection);

        context.fill(selectionX, textY - 1, selectionX + selectionWidth, textY + MinecraftClient.getInstance().textRenderer.fontHeight + 1, 0x993366CC);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOver(mouseX, mouseY)) {
            setFocused(true);
            cursorPosition = getCharacterIndexAt(mouseX);
            clearSelection();
            return true;
        }
        return false;
    }

    private int getCharacterIndexAt(double mouseX) {
        if (value.isEmpty()) return 0;

        int relativeX = (int) (mouseX - getX() - paddingLeft);
        for (int i = 0; i <= value.length(); i++) {
            String substr = value.substring(0, i);
            int width = MinecraftClient.getInstance().textRenderer.getWidth(substr);
            if (width >= relativeX) {
                return i;
            }
        }
        return value.length();
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) {
            cursorPosition = value.length();
            clearSelection();
            showCursor = true;
            lastBlinkTime = System.currentTimeMillis();
        }
    }

    public String getValue() {
        return value;
    }

    public UIInput setValue(String value) {
        String oldValue = this.value;
        this.value = value == null ? "" : value;
        cursorPosition = Math.min(cursorPosition, this.value.length());
        clearSelection();
        triggerChange(this.value, oldValue);
        return this;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public UIInput onChange(Value callback) {
        addEventHandler("change", callback);
        return this;
    }

    private void triggerChange(String newValue, String oldValue) {
        executeEventHandler("change", this, newValue, oldValue);
    }

    public void handleCharTyped(char character) {
        if (!isFocused() || !isValidCharacter(character)) return;

        if (hasSelection()) {
            deleteSelection();
        }

        String newValue = value.substring(0, cursorPosition) + character + value.substring(cursorPosition);
        setValue(newValue);
        cursorPosition++;
    }

    public void handleKeyPressed(int keyCode) {
        if (!isFocused()) return;

        boolean ctrl = hasControlDown();
        boolean shift = hasShiftDown();

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPosition > 0) {
                    setValue(value.substring(0, cursorPosition - 1) + value.substring(cursorPosition));
                    cursorPosition--;
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPosition < value.length()) {
                    setValue(value.substring(0, cursorPosition) + value.substring(cursorPosition + 1));
                }
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (shift) {
                    if (selectionStart == selectionEnd) selectionStart = cursorPosition;
                    cursorPosition = Math.max(0, cursorPosition - 1);
                    selectionEnd = cursorPosition;
                } else {
                    cursorPosition = Math.max(0, cursorPosition - 1);
                    clearSelection();
                }
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (shift) {
                    if (selectionStart == selectionEnd) selectionStart = cursorPosition;
                    cursorPosition = Math.min(value.length(), cursorPosition + 1);
                    selectionEnd = cursorPosition;
                } else {
                    cursorPosition = Math.min(value.length(), cursorPosition + 1);
                    clearSelection();
                }
            }
            case GLFW.GLFW_KEY_HOME -> {
                if (shift) {
                    if (selectionStart == selectionEnd) selectionStart = cursorPosition;
                    cursorPosition = 0;
                    selectionEnd = cursorPosition;
                } else {
                    cursorPosition = 0;
                    clearSelection();
                }
            }
            case GLFW.GLFW_KEY_END -> {
                if (shift) {
                    if (selectionStart == selectionEnd) selectionStart = cursorPosition;
                    cursorPosition = value.length();
                    selectionEnd = cursorPosition;
                } else {
                    cursorPosition = value.length();
                    clearSelection();
                }
            }
            case GLFW.GLFW_KEY_A -> {
                if (ctrl) {
                    selectAll();
                }
            }
            case GLFW.GLFW_KEY_C -> {
                if (ctrl && hasSelection()) {
                    copySelection();
                }
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) {
                    pasteFromClipboard();
                }
            }
            case GLFW.GLFW_KEY_X -> {
                if (ctrl && hasSelection()) {
                    copySelection();
                    deleteSelection();
                }
            }
        }

        showCursor = true;
        lastBlinkTime = System.currentTimeMillis();
    }

    private void deleteSelection() {
        if (!hasSelection()) return;

        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);

        setValue(value.substring(0, start) + value.substring(end));
        cursorPosition = start;
        clearSelection();
    }

    private void clearSelection() {
        selectionStart = cursorPosition;
        selectionEnd = cursorPosition;
    }

    private void selectAll() {
        selectionStart = 0;
        selectionEnd = value.length();
        cursorPosition = value.length();
    }

    private void copySelection() {
        if (!hasSelection()) return;

        int start = Math.min(selectionStart, selectionEnd);
        int end = Math.max(selectionStart, selectionEnd);
        String selectedText = value.substring(start, end);

        try {
            MinecraftClient.getInstance().keyboard.setClipboard(selectedText);
        } catch (Exception e) {
        }
    }

    private void pasteFromClipboard() {
        try {
            String clipboardText = MinecraftClient.getInstance().keyboard.getClipboard();
            if (clipboardText != null && !clipboardText.isEmpty()) {
                if (hasSelection()) {
                    deleteSelection();
                }

                String filteredText = filterClipboardText(clipboardText);
                String newValue = value.substring(0, cursorPosition) + filteredText + value.substring(cursorPosition);
                setValue(newValue);
                cursorPosition += filteredText.length();
            }
        } catch (Exception e) {
        }
    }

    private String filterClipboardText(String text) {
        StringBuilder filtered = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (isValidCharacter(c)) {
                filtered.append(c);
            }
        }
        return filtered.toString();
    }

    private boolean isValidCharacter(char character) {
        return character >= 32 && character != 127;
    }

    private boolean hasControlDown() {
        return net.minecraft.client.gui.screen.Screen.hasControlDown();
    }

    private boolean hasShiftDown() {
        return net.minecraft.client.gui.screen.Screen.hasShiftDown();
    }
}