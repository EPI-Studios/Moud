package com.moud.core.input;

import java.util.List;

public record InputAction(String id, String displayName, List<InputBinding> defaultBindings) { }
