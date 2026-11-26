package com.example.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

public final class PackIcons {
    public record Icon(Identifier id, int width, int height) {
    }

    private static final Map<String, Icon> CACHE = new HashMap<>();
    private static int counter;

    private PackIcons() {
    }

    public static Icon get(String packName) {
        if (CACHE.containsKey(packName)) {
            return CACHE.get(packName);
        }
        Icon icon = load(packName);
        CACHE.put(packName, icon);
        return icon;
    }

    private static Icon load(String packName) {
        try {
            byte[] data = PackTools.readEntry(packName, "pack.png");
            if (data == null) {
                return null;
            }
            NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
            Identifier id = Identifier.fromNamespaceAndPath("pack_switcher", "icon/" + counter);
            counter++;
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "pack_switcher_icon", image));
            return new Icon(id, image.getWidth(), image.getHeight());
        } catch (Exception e) {
            return null;
        }
    }
}