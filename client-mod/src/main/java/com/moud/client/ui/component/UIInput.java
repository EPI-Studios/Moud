package com.moud.client.ui.component;

import com.moud.client.api.service.UIService;
import com.moud.client.ui.UIFocusManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public class UIInput extends UIComponent {
    private volatile String value = "";
    private final String placeholder;
    private volatile int cursorPosition = 0;
    private volatile int selectionStart = 0;
    private volatile int selectionEnd = 0;
    private volatile long lastBlinkTime = 0;
    private volatile boolean showCursor = true;

    public UIInput(String placeholder, UIService service) {
        super("input", service);
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

        int textCol = parseColor(value.isEmpty() && !focused ? "#999999" : textColor, opacity);
        int textX = (int) getX() + (int) paddingLeft;
        int textY = (int) (getY() + (getHeight() - MinecraftClient.getInstance().textRenderer.fontHeight) / 2f);

        if (hasSelection()) {
            renderSelection(context, displayText, textX, textY);
        }

        context.drawText(MinecraftClient.getInstance().textRenderer, net.minecraft.text.Text.literal(displayText), textX, textY, textCol, false);

        if (focused && shouldShowCursor()) {
            renderCursor(context, displayText, textX, textY);
        }
    }

    private String getDisplayText() {
        if (!value.isEmpty()) return value;
        if (focused) return "";
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
            UIFocusManager.setFocus(this);
            cursorPosition = getCharacterIndexAt(mouseX);
            clearSelection();
            return true;
        }
        return false;
    }
    private boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
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

    @HostAccess.Export
    public String getValue() {
        return value;
    }

    @HostAccess.Export
    public UIInput setValue(String value) {
        String oldValue = this.value;
        this.value = value == null ? "" : value;
        cursorPosition = Math.min(cursorPosition, this.value.length());
        clearSelection();
        triggerChange(this.value, oldValue);
        return this;
    }

    @HostAccess.Export
    public String getPlaceholder() {
        return placeholder;
    }

    @HostAccess.Export
    public UIInput onChange(Value callback) {
        if (callback != null && callback.canExecute()) {
            eventHandlers.put("change", callback);
        }
        return this;
    }

    private void triggerChange(String newValue, String oldValue) {
        if (serverControlled) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("value", newValue);
            payload.put("previousValue", oldValue);
            notifyServerInteraction("change", payload);
        }
        executeEventHandler("change", this, newValue, oldValue);
    }

    public void handleCharTyped(char character) {
        if (!focused || !isValidCharacter(character)) return;

        if (hasSelection()) {
            deleteSelection();
        }

        String newValue = value.substring(0, cursorPosition) + character + value.substring(cursorPosition);
        setValue(newValue);
        cursorPosition++;
    }

    @Override
    public void triggerFocus() {
        this.focused = true;
        super.triggerFocus();
    }

    @Override
    public void triggerBlur() {
        this.focused = false;
        super.triggerBlur();
    }

    @HostAccess.Export
    public UIInput onSubmit(Value callback) {
        if (callback != null && callback.canExecute()) {
            eventHandlers.put("submit", callback);
        }
        return this;
    }

    private void triggerSubmit() {
        if (serverControlled) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("value", this.value);
            notifyServerInteraction("submit", payload);
        }
        executeEventHandler("submit", this, this.value);
    }


    public boolean handleKeyPressed(int keyCode) {
        if (!focused) return false;

        boolean ctrl = hasControlDown();
        boolean shift = hasShiftDown();

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            UIFocusManager.clearFocus();
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            triggerSubmit();
            UIFocusManager.clearFocus();
            return true;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE:
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPosition > 0) {
                    setValue(value.substring(0, cursorPosition - 1) + value.substring(cursorPosition));
                    cursorPosition--;
                }
                break;
            case GLFW.GLFW_KEY_DELETE:
                if (hasSelection()) {
                    deleteSelection();
                } else if (cursorPosition < value.length()) {
                    setValue(value.substring(0, cursorPosition) + value.substring(cursorPosition + 1));
                }
                break;
            case GLFW.GLFW_KEY_LEFT:
                if (shift) {
                    if (selectionStart == selectionEnd) selectionStart = cursorPosition;
                    cursorPosition = Math.max(0, cursorPosition - 1);
                    selectionEnd = cursorPosition;
                } else {
                    cursorPosition = Math.max(0, cursorPosition - 1);
                    clearSelection();
                }
                break;
            case GLFW.GLFW_KEY_RIGHT:
                if (shift) {
                    if (selectionStart == selectionEnd) selectionStart = cursorPosition;
                    cursorPosition = Math.min(value.length(), cursorPosition + 1);
                    selectionEnd = cursorPosition;
                } else {
                    cursorPosition = Math.min(value.length(), cursorPosition + 1);
                    clearSelection();
                }
                break;
            case GLFW.GLFW_KEY_HOME:
                if (shift) {
                    if (selectionStart == selectionEnd) selectionStart = cursorPosition;
                    cursorPosition = 0;
                    selectionEnd = cursorPosition;
                } else {
                    cursorPosition = 0;
                    clearSelection();
                }
                break;
            case GLFW.GLFW_KEY_END:
                if (shift) {
                    if (selectionStart == selectionEnd) selectionStart = cursorPosition;
                    cursorPosition = value.length();
                    selectionEnd = cursorPosition;
                } else {
                    cursorPosition = value.length();
                    clearSelection();
                }
                break;
            case GLFW.GLFW_KEY_A:
                if (ctrl) {
                    selectAll();
                }
                break;
            case GLFW.GLFW_KEY_C:
                if (ctrl && hasSelection()) {
                    copySelection();
                }
                break;
            case GLFW.GLFW_KEY_V:
                if (ctrl) {
                    pasteFromClipboard();
                }
                break;
            case GLFW.GLFW_KEY_X:
                if (ctrl && hasSelection()) {
                    copySelection();
                    deleteSelection();
                }
                break;
            default:
                return false;
        }

        showCursor = true;
        lastBlinkTime = System.currentTimeMillis();
        return true;
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
        } catch (Exception e) {}
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
        } catch (Exception e) {}
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
