package com.zenya.module.modules;

import com.zenya.module.Category;
import com.zenya.module.Module;

/**
 * Stand-in for a module the loader knows by name but has no implementation for.
 *
 * <p>Registering a stub keeps the entry in the GUI and preserves its bind and config
 * values; every hook is inherited empty, so toggling it does nothing.
 */
public class StubModule extends Module {

	public StubModule(String name, Category category) {
		super(name, category);
	}
}
