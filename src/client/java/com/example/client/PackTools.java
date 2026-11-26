package com.example.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class PackTools {
    private static final String TEXTURE_ROOT = "assets/minecraft/textures/";
    private static final String[] TEXTURE_FOLDERS = {"block", "item"};
    private static final String MCMETA =
            "{\"pack\":{\"min_format\":[88,0],\"max_format\":88,\"description\":\"Made with Pack Switcher\"}}";

    private PackTools() {
    }

    public static Path packsFolder() {
        return FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
    }

    public static List<String> listTextures(String packName) {
        List<String> result = new ArrayList<>();
        Path pack = packsFolder().resolve(packName);
        try {
            if (Files.isDirectory(pack)) {
                for (String folder : TEXTURE_FOLDERS) {
                    Path dir = pack.resolve(TEXTURE_ROOT + folder);
                    if (!Files.isDirectory(dir)) {
                        continue;
                    }
                    try (Stream<Path> files = Files.list(dir)) {
                        files.map(path -> path.getFileName().toString())
                                .filter(name -> name.endsWith(".png"))
                                .forEach(name -> result.add(folder + "/" + name.substring(0, name.length() - 4)));
                    }
                }
            } else if (Files.isRegularFile(pack)) {
                try (ZipFile zip = new ZipFile(pack.toFile())) {
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement().getName();
                        for (String folder : TEXTURE_FOLDERS) {
                            String prefix = TEXTURE_ROOT + folder + "/";
                            if (name.startsWith(prefix) && name.endsWith(".png") && name.indexOf('/', prefix.length()) < 0) {
                                result.add(folder + "/" + name.substring(prefix.length(), name.length() - 4));
                            }
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    public static byte[] readEntry(String packName, String entry) throws IOException {
        Path pack = packsFolder().resolve(packName);
        if (Files.isDirectory(pack)) {
            Path file = pack.resolve(entry);
            return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        }
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            ZipEntry zipEntry = zip.getEntry(entry);
            if (zipEntry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(zipEntry)) {
                return in.readAllBytes();
            }
        }
    }

    private static void writeEntry(ZipOutputStream out, Set<String> written, String name, byte[] data) throws IOException {
        if (!written.add(name)) {
            return;
        }
        out.putNextEntry(new ZipEntry(name));
        out.write(data);
        out.closeEntry();
    }

    private static void copyMainPack(ZipOutputStream out, Set<String> written, String mainPack, Set<String> skip) throws IOException {
        Path pack = packsFolder().resolve(mainPack);
        if (Files.isDirectory(pack)) {
            try (Stream<Path> walk = Files.walk(pack)) {
                List<Path> files = walk.filter(Files::isRegularFile).toList();
                for (Path file : files) {
                    String relative = pack.relativize(file).toString().replace('\\', '/');
                    if (relative.equals("pack.mcmeta") || skip.contains(relative)) {
                        continue;
                    }
                    writeEntry(out, written, relative, Files.readAllBytes(file));
                }
            }
            return;
        }
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || name.equals("pack.mcmeta") || skip.contains(name)) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    writeEntry(out, written, name, in.readAllBytes());
                }
            }
        }
    }

    private static String sanitizeName(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").trim();
        while (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    public static String buildSwapped(String mainPack, List<String> targets, List<String> sourcePacks, List<String> sourceTextures, String requestedName) throws IOException {
        Path folder = packsFolder();
        Files.createDirectories(folder);

        String base = sanitizeName(requestedName);
        if (base.isEmpty()) {
            String mainBase = mainPack.endsWith(".zip") ? mainPack.substring(0, mainPack.length() - 4) : mainPack;
            base = mainBase + " Mix";
        }
        String name = base + ".zip";
        int number = 2;
        while (Files.exists(folder.resolve(name))) {
            name = base + " " + number + ".zip";
            number++;
        }

        Set<String> skip = new HashSet<>();
        for (String target : targets) {
            String entry = TEXTURE_ROOT + target + ".png";
            skip.add(entry);
            skip.add(entry + ".mcmeta");
        }

        Set<String> written = new HashSet<>();
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(folder.resolve(name)))) {
            writeEntry(out, written, "pack.mcmeta", MCMETA.getBytes(StandardCharsets.UTF_8));
            copyMainPack(out, written, mainPack, skip);
            for (int i = 0; i < targets.size(); i++) {
                String targetEntry = TEXTURE_ROOT + targets.get(i) + ".png";
                String sourceEntry = TEXTURE_ROOT + sourceTextures.get(i) + ".png";
                byte[] image = readEntry(sourcePacks.get(i), sourceEntry);
                if (image != null) {
                    writeEntry(out, written, targetEntry, image);
                }
                byte[] meta = readEntry(sourcePacks.get(i), sourceEntry + ".mcmeta");
                if (meta != null) {
                    writeEntry(out, written, targetEntry + ".mcmeta", meta);
                }
            }
        }
        return name;
    }
}