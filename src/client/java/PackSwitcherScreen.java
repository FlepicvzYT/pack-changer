package com.example.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class PackSwitcherScreen extends Screen {
    private static final String[] PHASE_TITLES = {"1. MAIN PACK", "2. SWAP OUT", "3. SWAP FROM", "4. SWAP IN"};
    private static final String[] PHASE_HINTS = {
            "Pick the main pack", "Pick a texture to replace", "Pick a pack to swap from", "Pick the replacement"
    };
    private static final String BAD_NAME_CHARS = "\\/:*?\"<>|";

    private final List<String> activePacks = new ArrayList<>();
    private final List<String> inactivePacks = new ArrayList<>();
    private final List<String> swapTargets = new ArrayList<>();
    private final List<String> swapPacks = new ArrayList<>();
    private final List<String> swapSources = new ArrayList<>();
    private List<String> mainTextures = new ArrayList<>();
    private List<String> sourceTextures = new ArrayList<>();
    private String mainPack;
    private String targetTexture;
    private String sourcePack;
    private String pendingPack;
    private boolean naming;
    private String nameInput = "";
    private int craftPhase;
    private final int rowH = 18;
    private final int buttonW = 100;
    private final int buttonH = 20;
    private int tab;
    private String search = "";
    private String status = "";
    private int activeScroll;
    private int inactiveScroll;
    private int craftScroll;
    private int swapScroll;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int buttonX;
    private int applyX;
    private int buttonY;
    private int packsTabX;
    private int craftTabX;
    private int searchX;
    private int searchY;
    private int searchW;
    private int colW;
    private int leftX;
    private int rightX;
    private int listY;
    private int maxRows;
    private int popupX;
    private int popupY;
    private final int popupW = 240;
    private final int popupH = 110;

    public PackSwitcherScreen() {
        super(Component.literal("Pack Switcher"));
        loadPacks();
    }

    private void loadPacks() {
        List<String> selected = new ArrayList<>();
        PackRepository repository = Minecraft.getInstance().getResourcePackRepository();
        for (String id : repository.getSelectedIds()) {
            if (id.startsWith("file/")) {
                selected.add(id.substring(5));
            }
        }
        Path folder = PackTools.packsFolder();
        if (!Files.isDirectory(folder)) {
            return;
        }
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".zip") || Files.isDirectory(folder.resolve(name)))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(found::add);
        } catch (IOException ignored) {
        }
        for (int i = selected.size() - 1; i >= 0; i--) {
            if (found.contains(selected.get(i))) {
                activePacks.add(selected.get(i));
            }
        }
        for (String name : found) {
            if (!selected.contains(name)) {
                inactivePacks.add(name);
            }
        }
    }

    private void applyPacks() {
        Minecraft minecraft = Minecraft.getInstance();
        PackRepository repository = minecraft.getResourcePackRepository();
        repository.reload();
        List<String> ids = new ArrayList<>();
        for (String id : repository.getSelectedIds()) {
            if (!id.startsWith("file/")) {
                ids.add(id);
            }
        }
        for (int i = activePacks.size() - 1; i >= 0; i--) {
            String id = "file/" + activePacks.get(i);
            if (repository.isAvailable(id)) {
                ids.add(id);
            }
        }
        repository.setSelected(ids);
        minecraft.options.updateResourcePacks(repository);
        status = "Applied " + activePacks.size() + " pack(s)";
    }

    private void openNaming() {
        if (mainPack == null || swapTargets.isEmpty()) {
            return;
        }
        naming = true;
        nameInput = "";
    }

    private void confirmBuild() {
        naming = false;
        buildPack(nameInput);
    }

    private void buildPack(String requestedName) {
        if (mainPack == null || swapTargets.isEmpty()) {
            return;
        }
        try {
            String name = PackTools.buildSwapped(mainPack, swapTargets, swapPacks, swapSources, requestedName);
            inactivePacks.add(name);
            inactivePacks.sort(String.CASE_INSENSITIVE_ORDER);
            clearCraft();
            enterPhase(0);
            status = "Built: " + clean(name);
        } catch (IOException e) {
            status = "Build failed";
        }
    }

    private void clearCraft() {
        swapTargets.clear();
        swapPacks.clear();
        swapSources.clear();
        mainPack = null;
        targetTexture = null;
        sourcePack = null;
        mainTextures = new ArrayList<>();
        sourceTextures = new ArrayList<>();
    }

    @Override
    protected void init() {
        panelW = Math.min(420, this.width - 40);
        panelH = Math.min(260, this.height - 40);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        buttonX = panelX + panelW - buttonW - 16;
        applyX = buttonX - buttonW - 10;
        buttonY = panelY + panelH - buttonH - 16;
        packsTabX = panelX + 124;
        craftTabX = packsTabX + this.font.width("PACKS") + 18;
        searchW = Math.max(60, Math.min(150, panelW - 270));
        searchX = panelX + panelW - 16 - searchW;
        searchY = panelY + 12;
        colW = (panelW - 48) / 2;
        leftX = panelX + 16;
        rightX = panelX + 32 + colW;
        listY = panelY + 74;
        maxRows = Math.max(1, (buttonY - 12 - listY) / rowH);
        popupX = (this.width - popupW) / 2;
        popupY = (this.height - popupH) / 2;
        clampScrolls();
    }

    private String clean(String name) {
        return name.endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
    }

    private String shortName(String texture) {
        return texture.substring(texture.indexOf('/') + 1);
    }

    private List<String> allPacks() {
        List<String> all = new ArrayList<>(inactivePacks);
        all.addAll(activePacks);
        all.sort(String.CASE_INSENSITIVE_ORDER);
        return all;
    }

    private List<String> leftList() {
        switch (craftPhase) {
            case 1:
                return mainTextures;
            case 3:
                return sourceTextures;
            case 2:
                List<String> others = allPacks();
                others.remove(mainPack);
                return others;
            default:
                return allPacks();
        }
    }

    private List<String> swapLabels() {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < swapTargets.size(); i++) {
            String target = shortName(swapTargets.get(i));
            String source = shortName(swapSources.get(i));
            String from = target.equals(source) ? "" : source + " ";
            labels.add(target + " < " + from + "(" + clean(swapPacks.get(i)) + ")");
        }
        return labels;
    }

    private List<String> filtered(List<String> source) {
        if (search.isEmpty()) {
            return source;
        }
        String needle = search.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String name : source) {
            if (name.toLowerCase(Locale.ROOT).contains(needle)) {
                result.add(name);
            }
        }
        return result;
    }

    private void resetView() {
        search = "";
        status = "";
        activeScroll = 0;
        inactiveScroll = 0;
        craftScroll = 0;
    }

    private void searchChanged() {
        activeScroll = 0;
        inactiveScroll = 0;
        craftScroll = 0;
        status = "";
    }

    private int clamp(int scroll, int size) {
        return Math.max(0, Math.min(scroll, Math.max(0, size - maxRows)));
    }

    private void clampScrolls() {
        activeScroll = clamp(activeScroll, filtered(activePacks).size());
        inactiveScroll = clamp(inactiveScroll, filtered(inactivePacks).size());
        craftScroll = clamp(craftScroll, filtered(leftList()).size());
        swapScroll = clamp(swapScroll, swapTargets.size());
    }

    private boolean isOverRect(double x, double y, int rx, int ry, int rw, int rh) {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
    }

    private boolean isOver(int bx, double x, double y) {
        return isOverRect(x, y, bx, buttonY, buttonW, buttonH);
    }

    private boolean isOverTab(int tx, String label, double x, double y) {
        return x >= tx - 4 && x <= tx + this.font.width(label) + 4 && y >= panelY + 10 && y <= panelY + 30;
    }

    private int backX() {
        return leftX + colW - this.font.width("< BACK");
    }

    private boolean isOverBack(double x, double y) {
        return tab == 1 && craftPhase > 0 && x >= backX() - 4 && x <= leftX + colW && y >= panelY + 40 && y <= panelY + 56;
    }

    private boolean isOverColumn(int listX, double x, double y) {
        return x >= listX && x < listX + colW && y >= listY && y < listY + maxRows * rowH;
    }

    private int rowAt(int listX, int size, int scroll, double x, double y) {
        if (!isOverColumn(listX, x, y)) {
            return -1;
        }
        int visible = (int) ((y - listY) / rowH);
        int index = scroll + visible;
        return index < size ? index : -1;
    }

    private String fitTo(String text, int limit) {
        if (this.font.width(text) <= limit) {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && this.font.width(cut + "..") > limit) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "..";
    }

    private String count(List<String> all) {
        int shown = filtered(all).size();
        return search.isEmpty() ? String.valueOf(all.size()) : shown + "/" + all.size();
    }

    private void switchTab(int newTab) {
        tab = newTab;
        resetView();
    }

    private void enterPhase(int phase) {
        craftPhase = phase;
        resetView();
        if (phase == 3 && targetTexture != null) {
            search = shortName(targetTexture);
        }
    }

    private void goBack() {
        if (craftPhase == 3) {
            enterPhase(2);
        } else if (craftPhase == 2) {
            enterPhase(1);
        } else if (craftPhase == 1) {
            clearCraft();
            enterPhase(0);
        }
    }

    private void craftClick(String item) {
        if (craftPhase == 0) {
            List<String> textures = PackTools.listTextures(item);
            if (textures.isEmpty()) {
                status = "No block or item textures found";
                return;
            }
            mainPack = item;
            mainTextures = textures;
            enterPhase(1);
        } else if (craftPhase == 1) {
            targetTexture = item;
            enterPhase(2);
        } else if (craftPhase == 2) {
            List<String> textures = PackTools.listTextures(item);
            if (textures.isEmpty()) {
                status = "No block or item textures found";
                return;
            }
            sourcePack = item;
            sourceTextures = textures;
            enterPhase(3);
        } else {
            addSwap(item);
        }
    }

    private void addSwap(String sourceTexture) {
        for (int i = swapTargets.size() - 1; i >= 0; i--) {
            if (swapTargets.get(i).equals(targetTexture)) {
                swapTargets.remove(i);
                swapPacks.remove(i);
                swapSources.remove(i);
            }
        }
        swapTargets.add(targetTexture);
        swapPacks.add(sourcePack);
        swapSources.add(sourceTexture);
        String done = shortName(targetTexture);
        enterPhase(1);
        status = "Swapped " + done;
    }

    private void removeSwap(int index) {
        swapTargets.remove(index);
        swapPacks.remove(index);
        swapSources.remove(index);
        clampScrolls();
    }

    private void confirmPending() {
        if (pendingPack == null) {
            return;
        }
        inactivePacks.remove(pendingPack);
        activePacks.add(0, pendingPack);
        pendingPack = null;
        status = "";
        clampScrolls();
    }

    private void drawBox(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String label, boolean hovered, int idleColor) {
        graphics.fill(x, y, x + w, y + h, hovered ? 0xFFFFFFFF : idleColor);
        int labelX = x + (w - this.font.width(label)) / 2;
        graphics.text(this.font, label, labelX, y + 6, hovered ? 0xFF000000 : 0xFFFFFFFF, false);
    }

    private void drawButton(GuiGraphicsExtractor graphics, int bx, String label, int mouseX, int mouseY, int idleColor) {
        drawBox(graphics, bx, buttonY, buttonW, buttonH, label, isOver(bx, mouseX, mouseY), idleColor);
    }

    private void drawTab(GuiGraphicsExtractor graphics, int tx, String label, boolean active, int mouseX, int mouseY) {
        boolean hovered = isOverTab(tx, label, mouseX, mouseY);
        int color = active ? 0xFFFFFFFF : (hovered ? 0xFFCCCCCC : 0xFF6A6A6A);
        graphics.text(this.font, label, tx, panelY + 16, color, false);
        if (active) {
            graphics.fill(tx, panelY + 28, tx + this.font.width(label), panelY + 30, 0xFFFFFFFF);
        }
    }

    private void drawSearch(GuiGraphicsExtractor graphics) {
        graphics.fill(searchX - 1, searchY - 1, searchX + searchW + 1, searchY + 17, 0x33FFFFFF);
        graphics.fill(searchX, searchY, searchX + searchW, searchY + 16, 0xFF161616);
        if (search.isEmpty()) {
            graphics.text(this.font, "Type to search...", searchX + 6, searchY + 4, 0xFF5A5A5A, false);
            return;
        }
        String shown = search;
        while (shown.length() > 1 && this.font.width(shown) > searchW - 18) {
            shown = shown.substring(1);
        }
        boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
        graphics.text(this.font, shown + (caret ? "_" : ""), searchX + 6, searchY + 4, 0xFFFFFFFF, false);
    }

    private void drawColumn(GuiGraphicsExtractor graphics, List<String> names, int listX, int scroll, int mouseX, int mouseY, int accent, String emptyText, boolean arrows) {
        if (names.isEmpty()) {
            graphics.text(this.font, emptyText, listX, listY, 0xFF5A5A5A, false);
            return;
        }
        int hoverIndex = rowAt(listX, names.size(), scroll, mouseX, mouseY);
        int rows = Math.min(names.size() - scroll, maxRows);
        int fitLimit = arrows ? colW - 52 : colW - 20;
        for (int i = 0; i < rows; i++) {
            int index = scroll + i;
            int rowY = listY + i * rowH;
            boolean hover = hoverIndex == index;
            graphics.fill(listX, rowY, listX + colW, rowY + rowH - 4, hover ? 0xFF262626 : 0xFF161616);
            graphics.fill(listX, rowY, listX + 2, rowY + rowH - 4, accent);
            graphics.text(this.font, fitTo(names.get(index), fitLimit), listX + 10, rowY + 3, 0xFFFFFFFF, false);
            if (arrows) {
                boolean overUp = hover && mouseX >= listX + colW - 30 && mouseX < listX + colW - 16;
                boolean overDown = hover && mouseX >= listX + colW - 16;
                int upColor = index == 0 ? 0xFF333333 : (overUp ? 0xFFFFFFFF : 0xFF8A8A8A);
                int downColor = index == names.size() - 1 ? 0xFF333333 : (overDown ? 0xFFFFFFFF : 0xFF8A8A8A);
                graphics.text(this.font, "^", listX + colW - 26, rowY + 3, upColor, false);
                graphics.text(this.font, "v", listX + colW - 13, rowY + 3, downColor, false);
            }
        }
        if (names.size() > maxRows) {
            int trackH = maxRows * rowH - 4;
            int trackX = listX + colW + 4;
            int thumbH = Math.max(10, trackH * maxRows / names.size());
            int maxScroll = names.size() - maxRows;
            int thumbY = listY + (trackH - thumbH) * scroll / maxScroll;
            graphics.fill(trackX, listY, trackX + 2, listY + trackH, 0xFF1A1A1A);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFF8A8A8A);
        }
    }

    private void drawHint(GuiGraphicsExtractor graphics, String idleHint) {
        String text = status.isEmpty() ? idleHint : status;
        graphics.text(this.font, fitTo(text, applyX - panelX - 28), panelX + 16, buttonY + 6, status.isEmpty() ? 0xFF5A5A5A : 0xFFFFFFFF, false);
    }

    private void drawPacksTab(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, "UNACTIVATED (" + count(inactivePacks) + ")", leftX, panelY + 44, 0xFF8A8A8A, false);
        graphics.text(this.font, "ACTIVATED (" + count(activePacks) + ")", rightX, panelY + 44, 0xFFFFFFFF, false);
        graphics.text(this.font, "Click a pack to turn it on", leftX, panelY + 58, 0xFF5A5A5A, false);
        graphics.text(this.font, "Top wins. ^ v to reorder", rightX, panelY + 58, 0xFF5A5A5A, false);

        String empty = search.isEmpty() ? "Empty" : "No matches";
        drawColumn(graphics, filtered(inactivePacks), leftX, inactiveScroll, mouseX, mouseY, 0xFF4A4A4A, empty, false);
        drawColumn(graphics, filtered(activePacks), rightX, activeScroll, mouseX, mouseY, 0xFFFFFFFF, empty, search.isEmpty());

        drawHint(graphics, "Scroll for more packs");
        drawButton(graphics, applyX, "Apply", mouseX, mouseY, 0xFF2E2E2E);
    }

    private String subLine() {
        switch (craftPhase) {
            case 1:
                return "Main: " + clean(mainPack);
            case 2:
                return "Replacing: " + shortName(targetTexture);
            case 3:
                return clean(sourcePack) + " > " + shortName(targetTexture);
            default:
                return "Choose the pack to start from";
        }
    }

    private void drawCraftingTab(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, PHASE_TITLES[craftPhase], leftX, panelY + 44, 0xFFFFFFFF, false);
        if (craftPhase > 0) {
            boolean backHover = isOverBack(mouseX, mouseY);
            graphics.text(this.font, "< BACK", backX(), panelY + 44, backHover ? 0xFFFFFFFF : 0xFFAAAAAA, false);
        }
        graphics.text(this.font, fitTo(subLine(), colW), leftX, panelY + 58, 0xFF5A5A5A, false);
        graphics.text(this.font, "SWAPS (" + swapTargets.size() + ")", rightX, panelY + 44, 0xFFFFFFFF, false);
        graphics.text(this.font, "Click a swap to remove it", rightX, panelY + 58, 0xFF5A5A5A, false);

        String leftEmpty = search.isEmpty() ? "Empty" : "No matches";
        int leftAccent = (craftPhase == 0 || craftPhase == 2) ? 0xFF4A4A4A : 0xFFFFFFFF;
        drawColumn(graphics, filtered(leftList()), leftX, craftScroll, mouseX, mouseY, leftAccent, leftEmpty, false);
        drawColumn(graphics, swapLabels(), rightX, swapScroll, mouseX, mouseY, 0xFFFFFFFF, "No swaps yet", false);

        drawHint(graphics, PHASE_HINTS[craftPhase]);

        if (mainPack == null || swapTargets.isEmpty()) {
            graphics.fill(applyX, buttonY, applyX + buttonW, buttonY + buttonH, 0xFF161616);
            int labelX = applyX + (buttonW - this.font.width("Build Pack")) / 2;
            graphics.text(this.font, "Build Pack", labelX, buttonY + 6, 0xFF4A4A4A, false);
        } else {
            drawButton(graphics, applyX, "Build Pack", mouseX, mouseY, 0xFF2E2E2E);
        }
    }

    private int confirmX() {
        return popupX + popupW - 16 - 90;
    }

    private int cancelX() {
        return confirmX() - 10 - 90;
    }

    private int popupButtonY() {
        return popupY + popupH - 16 - 20;
    }

    private void drawPopupFrame(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xAA000000);
        graphics.fill(popupX - 1, popupY - 1, popupX + popupW + 1, popupY + popupH + 1, 0x55FFFFFF);
        graphics.fill(popupX, popupY, popupX + popupW, popupY + popupH, 0xFF0D0D0D);
    }

    private void drawPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawPopupFrame(graphics);

        graphics.fill(popupX + 16, popupY + 16, popupX + 64, popupY + 64, 0x33FFFFFF);
        graphics.fill(popupX + 17, popupY + 17, popupX + 63, popupY + 63, 0xFF161616);
        PackIcons.Icon icon = PackIcons.get(pendingPack);
        if (icon != null) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, icon.id(), popupX + 17, popupY + 17, 0.0F, 0.0F, 46, 46, icon.width(), icon.height(), icon.width(), icon.height());
        } else {
            graphics.text(this.font, "?", popupX + 37, popupY + 37, 0xFF5A5A5A, false);
        }

        graphics.text(this.font, fitTo(clean(pendingPack), popupW - 92), popupX + 76, popupY + 20, 0xFFFFFFFF, false);
        graphics.text(this.font, "Turn this pack on?", popupX + 76, popupY + 36, 0xFF8A8A8A, false);
        graphics.text(this.font, "It goes on top.", popupX + 76, popupY + 50, 0xFF5A5A5A, false);

        int by = popupButtonY();
        drawBox(graphics, cancelX(), by, 90, 20, "Cancel", isOverRect(mouseX, mouseY, cancelX(), by, 90, 20), 0xFF1E1E1E);
        drawBox(graphics, confirmX(), by, 90, 20, "Confirm", isOverRect(mouseX, mouseY, confirmX(), by, 90, 20), 0xFF2E2E2E);
    }

    private void drawNamePopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        drawPopupFrame(graphics);

        graphics.text(this.font, "NAME YOUR PACK", popupX + 16, popupY + 16, 0xFFFFFFFF, false);

        int fieldX = popupX + 16;
        int fieldY = popupY + 34;
        int fieldW = popupW - 32;
        graphics.fill(fieldX - 1, fieldY - 1, fieldX + fieldW + 1, fieldY + 19, 0x33FFFFFF);
        graphics.fill(fieldX, fieldY, fieldX + fieldW, fieldY + 18, 0xFF161616);
        if (nameInput.isEmpty()) {
            graphics.text(this.font, "Pack name...", fieldX + 6, fieldY + 5, 0xFF5A5A5A, false);
        } else {
            String shown = nameInput;
            while (shown.length() > 1 && this.font.width(shown) > fieldW - 20) {
                shown = shown.substring(1);
            }
            boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
            graphics.text(this.font, shown + (caret ? "_" : ""), fieldX + 6, fieldY + 5, 0xFFFFFFFF, false);
        }
        graphics.text(this.font, "Leave empty for a default name.", popupX + 16, popupY + 58, 0xFF5A5A5A, false);

        int by = popupButtonY();
        drawBox(graphics, cancelX(), by, 90, 20, "Cancel", isOverRect(mouseX, mouseY, cancelX(), by, 90, 20), 0xFF1E1E1E);
        drawBox(graphics, confirmX(), by, 90, 20, "Build", isOverRect(mouseX, mouseY, confirmX(), by, 90, 20), 0xFF2E2E2E);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean popup = pendingPack != null || naming;
        int mx = popup ? -1000 : mouseX;
        int my = popup ? -1000 : mouseY;

        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
        graphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0x33FFFFFF);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF0D0D0D);

        graphics.text(this.font, "PACK SWITCHER", panelX + 16, panelY + 16, 0xFFFFFFFF, false);
        drawTab(graphics, packsTabX, "PACKS", tab == 0, mx, my);
        drawTab(graphics, craftTabX, "CRAFTING", tab == 1, mx, my);
        drawSearch(graphics);

        if (tab == 0) {
            drawPacksTab(graphics, mx, my);
        } else {
            drawCraftingTab(graphics, mx, my);
        }

        drawButton(graphics, buttonX, "Done", mx, my, 0xFF1E1E1E);

        if (pendingPack != null) {
            drawPopup(graphics, mouseX, mouseY);
        } else if (naming) {
            drawNamePopup(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x();
        double y = event.y();
        if (pendingPack != null) {
            if (event.button() == 0) {
                if (isOverRect(x, y, confirmX(), popupButtonY(), 90, 20)) {
                    confirmPending();
                } else if (isOverRect(x, y, cancelX(), popupButtonY(), 90, 20)) {
                    pendingPack = null;
                }
            }
            return true;
        }
        if (naming) {
            if (event.button() == 0) {
                if (isOverRect(x, y, confirmX(), popupButtonY(), 90, 20)) {
                    confirmBuild();
                } else if (isOverRect(x, y, cancelX(), popupButtonY(), 90, 20)) {
                    naming = false;
                }
            }
            return true;
        }
        if (event.button() == 0) {
            if (isOver(buttonX, x, y)) {
                this.onClose();
                return true;
            }
            if (isOverTab(packsTabX, "PACKS", x, y)) {
                switchTab(0);
                return true;
            }
            if (isOverTab(craftTabX, "CRAFTING", x, y)) {
                switchTab(1);
                return true;
            }
            if (tab == 0) {
                if (isOver(applyX, x, y)) {
                    applyPacks();
                    return true;
                }
                List<String> left = filtered(inactivePacks);
                int index = rowAt(leftX, left.size(), inactiveScroll, x, y);
                if (index >= 0) {
                    pendingPack = left.get(index);
                    return true;
                }
                List<String> right = filtered(activePacks);
                index = rowAt(rightX, right.size(), activeScroll, x, y);
                if (index >= 0) {
                    String name = right.get(index);
                    if (search.isEmpty() && x >= rightX + colW - 30) {
                        int position = activePacks.indexOf(name);
                        if (x < rightX + colW - 16) {
                            if (position > 0) {
                                Collections.swap(activePacks, position, position - 1);
                            }
                        } else if (position < activePacks.size() - 1) {
                            Collections.swap(activePacks, position, position + 1);
                        }
                        return true;
                    }
                    activePacks.remove(name);
                    inactivePacks.add(name);
                    inactivePacks.sort(String.CASE_INSENSITIVE_ORDER);
                    status = "";
                    clampScrolls();
                    return true;
                }
            } else {
                if (isOver(applyX, x, y)) {
                    openNaming();
                    return true;
                }
                if (isOverBack(x, y)) {
                    goBack();
                    return true;
                }
                List<String> left = filtered(leftList());
                int index = rowAt(leftX, left.size(), craftScroll, x, y);
                if (index >= 0) {
                    craftClick(left.get(index));
                    return true;
                }
                index = rowAt(rightX, swapTargets.size(), swapScroll, x, y);
                if (index >= 0) {
                    removeSwap(index);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pendingPack != null || naming) {
            return true;
        }
        int amount = (int) -Math.signum(scrollY);
        if (tab == 0) {
            if (isOverColumn(leftX, mouseX, mouseY)) {
                inactiveScroll = clamp(inactiveScroll + amount, filtered(inactivePacks).size());
                return true;
            }
            if (isOverColumn(rightX, mouseX, mouseY)) {
                activeScroll = clamp(activeScroll + amount, filtered(activePacks).size());
                return true;
            }
        } else {
            if (isOverColumn(leftX, mouseX, mouseY)) {
                craftScroll = clamp(craftScroll + amount, filtered(leftList()).size());
                return true;
            }
            if (isOverColumn(rightX, mouseX, mouseY)) {
                swapScroll = clamp(swapScroll + amount, swapTargets.size());
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (pendingPack != null) {
            return true;
        }
        if (naming) {
            String typed = event.codepointAsString();
            if (event.isAllowedChatCharacter() && nameInput.length() < 40 && !BAD_NAME_CHARS.contains(typed)) {
                nameInput += typed;
            }
            return true;
        }
        if (event.isAllowedChatCharacter() && search.length() < 40) {
            search += event.codepointAsString();
            searchChanged();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (pendingPack != null) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                pendingPack = null;
            } else if (event.key() == InputConstants.KEY_RETURN) {
                confirmPending();
            }
            return true;
        }
        if (naming) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                naming = false;
            } else if (event.key() == InputConstants.KEY_RETURN) {
                confirmBuild();
            } else if (event.key() == InputConstants.KEY_BACKSPACE && !nameInput.isEmpty()) {
                nameInput = nameInput.substring(0, nameInput.length() - 1);
            }
            return true;
        }
        if (event.key() == InputConstants.KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            searchChanged();
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE && !search.isEmpty()) {
            search = "";
            searchChanged();
            return true;
        }
        return super.keyPressed(event);
    }
}