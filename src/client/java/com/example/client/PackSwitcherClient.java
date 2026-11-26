package com.example.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class PackSwitcherClient implements ClientModInitializer {
	private static KeyMapping openKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath("pack_switcher", "main")
		);

		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.pack_switcher.open",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_K,
				category
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				client.gui.setScreen(new PackSwitcherScreen());
			}
		});
	}
}