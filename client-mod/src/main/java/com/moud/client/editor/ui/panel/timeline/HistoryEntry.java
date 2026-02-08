package com.moud.client.editor.ui.panel.timeline;

import java.util.List;

public record HistoryEntry(String description, List<HistoryAction> undoActions, List<HistoryAction> redoActions) {
}
