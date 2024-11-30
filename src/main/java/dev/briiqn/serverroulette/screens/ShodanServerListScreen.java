package dev.briiqn.serverroulette.screens;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.briiqn.serverroulette.shodan.api.ServerData;
import dev.briiqn.serverroulette.shodan.api.ShodanServerManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class ShodanServerListScreen extends Screen {
    private final Screen parent;
    private static HashSet<List<ServerData>> cachedPages = new HashSet<>();
    private static HashSet<ServerData> filteredServers = new LinkedHashSet<>();
    private static int currentPage = 0;
    private static final int SERVERS_PER_PAGE = 15;
    private static final Object lock = new Object();

    private static TextFieldWidget playerCountFilter;
    private static TextFieldWidget regionFilter;
    private static TextFieldWidget descriptionFilter;
    private static TextFieldWidget versionFilter;
    private TextFieldWidget apiKeyInput;
    private static boolean showApiKeyInput = false;
    private static boolean showFilters = false;

    private static String lastPlayerCountFilter = "";
    private static String lastRegionFilter = "";
    private static String lastDescriptionFilter = "";
    private static String lastVersionFilter = "";
    private static final int UPDATE_INTERVAL = 1;

    private static ExecutorService executorService = Executors.newSingleThreadExecutor();
    private  static  CompletableFuture<Void> cacheTask;
    private static AtomicBoolean isCancelled = new AtomicBoolean(false);
    private static Set<String> uniqueIPs = new HashSet<>();
    private static boolean isCachingComplete = false;
    private static final  Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static final String CACHE_FILE_NAME = "shodanservers/exported";
    public ShodanServerListScreen(Screen parent) {
        super(Text.literal("Shodan Servers"));
        this.parent = parent;
        if (cachedPages.isEmpty()) {
            startCaching();
        }
    }

    private void startCaching() {
        if (cacheTask != null && !cacheTask.isDone()) {
            isCancelled.set(true);
            cacheTask.cancel(true);
        }
        isCancelled.set(false);
        isCachingComplete = false;
        cacheTask = CompletableFuture.runAsync(ShodanServerListScreen::cacheAllPages, executorService)
                .thenRun(() -> {
                    isCachingComplete = true;
                    applyFilters();
                    updateUI();
                });
    }
    private static List<ServerData> removeDuplicates(List<ServerData> servers) {
        Map<String, ServerData> uniqueServers = new LinkedHashMap<>();
        for (ServerData server : servers) {
            uniqueServers.putIfAbsent(server.ip, server);
        }
        return new ArrayList<>(uniqueServers.values());
    }

    private static void cacheAllPages() {
        int page = 0;
        List<ServerData> pageServers;
        int serverCount = 0;
        do {
            if (isCancelled.get()) {
                return;
            }
            ShodanServerManager.init(lastVersionFilter, lastRegionFilter, null, page);
            pageServers = new ArrayList<>(ShodanServerManager.servers);
            if(pageServers.size() > 0 && !ShodanServerManager.reachedEnd) {
                synchronized (cachedPages) {
                    cachedPages.add(removeDuplicates(pageServers));
                }
                applyFiltersToPage(pageServers);
                page++;
                serverCount += pageServers.size();
                if (serverCount % UPDATE_INTERVAL == 0) {
                    updateUI();
                }
            }
        } while (!pageServers.isEmpty() && !ShodanServerManager.reachedEnd && !isCancelled.get());
        updateUI();
    }
    private static void updateUI() {
        if (MinecraftClient.getInstance() != null && MinecraftClient.getInstance().currentScreen instanceof ShodanServerListScreen) {
            MinecraftClient.getInstance().execute(() -> {
                ShodanServerListScreen currentScreen = (ShodanServerListScreen) MinecraftClient.getInstance().currentScreen;
                currentScreen.init(MinecraftClient.getInstance(), MinecraftClient.getInstance().getWindow().getScaledWidth(), MinecraftClient.getInstance().getWindow().getScaledHeight());
            });
        }
    }
    private static void applyFiltersToPage(List<ServerData> pageServers) {
        List<ServerData> filteredPage = pageServers.stream()
                .filter(ShodanServerListScreen::filterByPlayerCount)
                .filter(ShodanServerListScreen::filterByRegion)
                .filter(ShodanServerListScreen::filterByDescription)
                .filter(ShodanServerListScreen::filterByVersion)
                .filter(ShodanServerListScreen::isUnique)
                .collect(Collectors.toList());
        synchronized (lock) {
            filteredServers.addAll(filteredPage);
        }
    }


    private static boolean isUnique(ServerData server) {
        return uniqueIPs.add(server.ip);
    }

    @Override
    protected void init() {
        clearChildren();

        int buttonWidth = 200;
        int buttonHeight = 20;
        int startY = (showFilters || showApiKeyInput) ? 120 : 50;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Export Servers"), button -> exportServers())
                .dimensions(this.width - 210, 20, 100, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Import Servers"), button -> importServers())
                .dimensions(this.width - 315, 20, 100, 20)
                .build());
        if (showApiKeyInput) {
            apiKeyInput = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 50, 200, 20, Text.literal(""));
            apiKeyInput.setPlaceholder(Text.literal("Enter Shodan Cookie"));
            apiKeyInput.setMaxLength(9999);
            this.addDrawableChild(apiKeyInput);

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Save Cookie"), button -> saveApiKey())
                    .dimensions(this.width / 2 - 100, 75, 200, 20)
                    .build());
        } else {
            this.addDrawableChild(ButtonWidget.builder(Text.literal(showFilters ? "Hide Filters" : "Show Filters"), button -> toggleFilters())
                    .dimensions(this.width - 105, 20, 100, 20)
                    .build());

            if (showFilters) {
                this.addDrawableChild(ButtonWidget.builder(Text.literal(showFilters ? "Hide Filters" : "Show Filters"), button -> toggleFilters())
                        .dimensions(this.width - 105, 20, 100, 20)
                        .build());

                if (showFilters) {
                    playerCountFilter = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 50, 200, 20, Text.literal(""));
                    playerCountFilter.setText(lastPlayerCountFilter);
                    playerCountFilter.setPlaceholder(Text.literal("Online Players (e.g., <10 or >5)"));
                    this.addDrawableChild(playerCountFilter);

                    regionFilter = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 75, 200, 20, Text.literal(""));
                    regionFilter.setText(lastRegionFilter);
                    regionFilter.setPlaceholder(Text.literal("Country (e.g., CA, US, DE)"));
                    this.addDrawableChild(regionFilter);

                    descriptionFilter = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 100, 200, 20, Text.literal(""));
                    descriptionFilter.setText(lastDescriptionFilter);
                    descriptionFilter.setPlaceholder(Text.literal("Description"));
                    this.addDrawableChild(descriptionFilter);

                    versionFilter = new TextFieldWidget(this.textRenderer, this.width / 2 - 100, 125, 200, 20, Text.literal(""));
                    versionFilter.setText(lastVersionFilter);
                    versionFilter.setPlaceholder(Text.literal("Server Version (e.g., 1.20.6)"));
                    this.addDrawableChild(versionFilter);

                    this.addDrawableChild(ButtonWidget.builder(Text.literal("Apply Filters"), button -> applyFilters())
                            .dimensions(this.width / 2 - 100, 150, 200, 20)
                            .build());

                    this.addDrawableChild(ButtonWidget.builder(Text.literal("Restart Search with Filters"), button -> restartSearchWithFilters())
                            .dimensions(this.width / 2 - 100, 175, 200, 20)
                            .build());
                }
            } else {
                List<ServerData> currentPageServers = getCurrentPageServers();

                for (int i = 0; i < SERVERS_PER_PAGE && i < currentPageServers.size(); i++) {
                    final ServerData server = currentPageServers.get(i);
                    int y = startY + i * (buttonHeight + 5);
                    String playerCount = (server != null)
                            ? " (" + server.onlinePlayers + "/" + server.maxPlayers + ")"
                            : " Error Getting Player Count (Modded Server?)";

                    ButtonWidget serverButton = ButtonWidget.builder(Text.literal(server.ip + playerCount + " "), button -> join(server))
                            .dimensions((this.width - buttonWidth) / 2, y, buttonWidth, buttonHeight)
                            .build();

                    this.addDrawableChild(serverButton);
                }

                if (currentPage > 0) {
                    this.addDrawableChild(ButtonWidget.builder(Text.literal("Previous"), button -> changePage(-1))
                            .dimensions(10, this.height - 30, 100, 20)
                            .build());
                }

                if ((currentPage + 1) * SERVERS_PER_PAGE < getTotalFilteredServers() ) {
                    this.addDrawableChild(ButtonWidget.builder(Text.literal("Next"), button -> changePage(1))
                            .dimensions(this.width - 110, this.height - 30, 100, 20)
                            .build());
                }
            }

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> this.client.setScreen(parent))
                    .dimensions((this.width - buttonWidth) / 2, this.height - 30, buttonWidth, 20)
                    .build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Refresh Servers"), button -> refreshServers())
                    .dimensions(10, 20, 100, 20)
                    .build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Join Random Server"), button -> joinRandomServer())
                    .dimensions(160, 20, 100, 20)
                    .build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Update Cookie"), button -> toggleApiKeyInput())
                    .dimensions(360, 20, 100, 20)
                    .build());
        }
    }

    private static List<ServerData> getCurrentPageServers() {
        synchronized (lock) {
            int startIndex = currentPage * SERVERS_PER_PAGE;
            int endIndex = Math.min(startIndex + SERVERS_PER_PAGE, filteredServers.size());
            if (startIndex < endIndex) {
                return new ArrayList<>(getFilteredServers(filteredServers,startIndex, endIndex));
            } else {
                return new ArrayList<>();
            }
        }
    }
    private static List<ServerData> getFilteredServers(HashSet<ServerData> filteredServers, int startIndex, int endIndex) {
        List<ServerData> result = new ArrayList<>();
        Iterator<ServerData> iterator = filteredServers.iterator();
        int index = 0;

        while (iterator.hasNext()) {
            ServerData server = iterator.next();
            if (index >= startIndex && index < endIndex) {
                result.add(server);
            }
            index++;
            if (index >= endIndex) {
                break;
            }
        }
        return result;
    }

    private static int getTotalFilteredServers() {
        synchronized (lock) {
            return filteredServers.size();
        }
    }

    private void joinRandomServer() {
        if (filteredServers.isEmpty()) {
            return;
        }

        int randomIndex = new Random().nextInt(filteredServers.size());
        Iterator<ServerData> iterator = filteredServers.iterator();

        ServerData randomServer = null;
        for (int i = 0; i <= randomIndex; i++) {
            randomServer = iterator.next();
        }

        join(randomServer);
    }
    private void toggleFilters() {
        showFilters = !showFilters;
        if (showFilters) {
            if (playerCountFilter != null) playerCountFilter.setText(lastPlayerCountFilter);
            if (regionFilter != null) regionFilter.setText(lastRegionFilter);
            if (descriptionFilter != null) descriptionFilter.setText(lastDescriptionFilter);
            if (versionFilter != null) versionFilter.setText(lastVersionFilter);
        }
        init();
    }

    private static void applyFilters() {
        lastPlayerCountFilter = playerCountFilter != null ? playerCountFilter.getText() : "";
        lastRegionFilter = regionFilter != null ? regionFilter.getText() : "";
        lastDescriptionFilter = descriptionFilter != null ? descriptionFilter.getText() : "";
        lastVersionFilter = versionFilter != null ? versionFilter.getText() : "";

        synchronized (lock) {
            for (List<ServerData> page : cachedPages) {
                applyFiltersToPage(page);
            }
        }
        currentPage = 0;
        updateUI();
    }

    private void restartSearchWithFilters() {
        applyFilters();

        synchronized (lock) {
            cachedPages.clear();
            filteredServers.clear();
        }
        currentPage = 0;

        if (cacheTask != null && !cacheTask.isDone()) {
            isCancelled.set(true);
            cacheTask.cancel(true);
        }

        startCaching();

        showFilters = false;
        init();
    }

    private static boolean filterByPlayerCount(ServerData server) {
        if (lastPlayerCountFilter.isEmpty()) return true;

        String filter = lastPlayerCountFilter.trim();
        if (server == null) return false;

        try {
            if (filter.startsWith("<")) {
                int count = Integer.parseInt(filter.substring(1));
                return server.onlinePlayers < count;
            } else if (filter.startsWith(">")) {
                int count = Integer.parseInt(filter.substring(1));
                return server.onlinePlayers > count;
            } else {
                int count = Integer.parseInt(filter);
                return server.onlinePlayers == count;
            }
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static boolean filterByRegion(ServerData server) {
 return true;
    }

    private static boolean filterByDescription(ServerData server) {
        if (lastDescriptionFilter.isEmpty()) return true;
        return server.description != null &&
                server.description.toLowerCase().contains(lastDescriptionFilter.toLowerCase());
    }

    private static boolean filterByVersion(ServerData server) {
        if (lastVersionFilter.isEmpty()) return true;
        return server.version != null &&
                server.version.toLowerCase().contains(lastVersionFilter.toLowerCase());
    }

    private void join(ServerData server) {
        ServerInfo serverInfo = new ServerInfo("Shodan Server", server.ip, ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(this, this.client, ServerAddress.parse(serverInfo.address), serverInfo, true, null);
    }

    private void changePage(int delta) {
        currentPage += delta;
        if (currentPage < 0) {
            currentPage = 0;
        }
        int maxPage = (getTotalFilteredServers() - 1) / SERVERS_PER_PAGE;
        if (currentPage > maxPage) {
            currentPage = maxPage;
        }
        init();
    }

    private void refreshServers() {
        synchronized (cachedPages) {
            cachedPages.clear();
        }
        synchronized (filteredServers) {
            filteredServers.clear();
        }
        uniqueIPs.clear();
        currentPage = 0;
        isCachingComplete = false;
        startCaching();
    }

    private void toggleApiKeyInput() {
        showApiKeyInput = !showApiKeyInput;
        showFilters = false;
        init();
    }

    private void saveApiKey() {
        String newApiKey = apiKeyInput.getText().trim();
        if (!newApiKey.isEmpty()) {
            ShodanServerManager.cookie = newApiKey;
            ShodanServerManager.saveConfig();
            refreshServers();
        }
        showApiKeyInput = false;
        init();
    }
    private void exportServers() {
        CompletableFuture.runAsync(() -> {
            try (Writer writer = new FileWriter(CACHE_FILE_NAME+System.currentTimeMillis()+".json")) {
                gson.toJson(cachedPages, writer);
                MinecraftClient.getInstance().execute(() -> {
                    System.out.println("Servers exported successfully!");
                });
            } catch (IOException e) {
                e.printStackTrace();
                MinecraftClient.getInstance().execute(() -> {
                    System.out.println("Failed to export servers: " + e.getMessage());
                });
            }
        });
    }

    private void importServers() {
        CompletableFuture.runAsync(() -> {
            try (Reader reader = new FileReader(CACHE_FILE_NAME)) {
                Type type = new TypeToken<HashSet<List<ServerData>>>(){}.getType();
                HashSet<List<ServerData>> importedPages = gson.fromJson(reader, type);

                synchronized (cachedPages) {
                    cachedPages.clear();
                    cachedPages.addAll(importedPages);
                }

                applyFilters();

                MinecraftClient.getInstance().execute(() -> {
                    System.out.println("Servers imported successfully!");
                    updateUI();
                });
            } catch (IOException e) {
                e.printStackTrace();
                MinecraftClient.getInstance().execute(() -> {
                    System.out.println("Failed to import servers: " + e.getMessage());
                });
            }
        });
    }
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);

        if (!isCachingComplete) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Loading servers... (" + getTotalFilteredServers() + " found)"), this.width / 2, this.height - 64, 0xFFFFFF);
        } else if (getTotalFilteredServers() == 0) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No servers found. Try refreshing or adjusting filters."), this.width / 2, this.height / 2, 0xFFFFFF);
        }


        if (!showFilters) {
            int startY = 50;
            List<ServerData> currentPageServers = getCurrentPageServers();

            for (int i = 0; i < currentPageServers.size(); i++) {
                int y = startY + (i * 25);
                if (mouseY >= y && mouseY <= y + 20) {
                    int x = (this.width - 200) / 2;
                    if (mouseX >= x && mouseX <= x + 200) {
                        ServerData server = currentPageServers.get(i);
                        if (server.description != null && !server.description.isEmpty()) {
                            context.drawTooltip(this.textRenderer, Text.literal(server.description), mouseX, mouseY);
                        }
                        String countryInfo = server.country != null ? server.country : "Unknown";
                        String versionInfo = server.version != null && !server.version.isEmpty() ? server.version : "Unknown version";
                        context.drawTooltip(this.textRenderer, Text.literal(countryInfo + " | " + versionInfo), mouseX, mouseY + 20);
                    }
                }
            }
        }
        String pageInfo = String.format("Page %d/%d", currentPage + 1, (getTotalFilteredServers() - 1) / SERVERS_PER_PAGE + 1);
        context.drawCenteredTextWithShadow(this.textRenderer, pageInfo, this.width / 2, this.height - 45, 0xFFFFFF);

        if (!showFilters && (
                !lastPlayerCountFilter.isEmpty() ||
                        !lastRegionFilter.isEmpty() ||
                        !lastDescriptionFilter.isEmpty() ||
                        !lastVersionFilter.isEmpty())) {
            String filterInfo = "Active Filters: ";
            if (!lastPlayerCountFilter.isEmpty()) filterInfo += "Players: " + lastPlayerCountFilter + " | ";
            if (!lastRegionFilter.isEmpty()) filterInfo += "Country: " + lastRegionFilter + " | ";
            if (!lastDescriptionFilter.isEmpty()) filterInfo += "Desc: " + lastDescriptionFilter + " | ";
            if (!lastVersionFilter.isEmpty()) filterInfo += "Version: " + lastVersionFilter + " | ";

            context.drawTextWithShadow(this.textRenderer, filterInfo, 10, 45, 0xFFFFFF);
        }
    }

    @Override
    public void removed() {
        super.removed();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
