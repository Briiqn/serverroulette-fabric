package dev.briiqn.serverroulette.shodan.api;

import net.minecraft.client.network.ServerInfo;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShodanServerManager {
public static List<ServerData> servers=new ArrayList<ServerData>();
private static ShodanAPI api = new ShodanAPI();
public static boolean reachedEnd=false;
public static String cookie="";
    public static void init(String version,String country, String city,int page) {
        loadConfig();
        if(!reachedEnd) {

            servers = parseServers(api.getMinecraftServers(cookie, version, country, city, page));
            if(servers.isEmpty()){
            }
        }
    }

    public static List<ServerData> parseServers(String html) {
        List<ServerData> servers = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements results = doc.select("div.result");

        for (Element result : results) {
            String blob = result.select("div.container").text();
            String ip = result.select("a.title").text();

            // ended up doing this anyway
            String versionRegex = "Version: ([\\d\\.]+ \\(Protocol \\d+\\))";
            String descriptionRegex = "Description: (.+?)\\s+Online Players:";
            String onlinePlayersRegex = "Online Players: (\\d+)";
            String maxPlayersRegex = "Maximum Players: (\\d+)";
            String locationRegex = "(.*?)([\\w\\s]+),\\s*([\\w\\s]+)"; //  provider, country,  city

            String version = "";
            String description = "";
            String country = "";
            String city = "";
            int onlinePlayers = 0;
            int maxPlayers = 0;

            System.out.println("Raw Blob: " + blob);

            Matcher versionMatcher = Pattern.compile(versionRegex).matcher(blob);
            if (versionMatcher.find()) {
                version = versionMatcher.group(1);
            }

            Matcher descriptionMatcher = Pattern.compile(descriptionRegex).matcher(blob);
            if (descriptionMatcher.find()) {
                description = descriptionMatcher.group(1).trim();
            }

            Matcher onlinePlayersMatcher = Pattern.compile(onlinePlayersRegex).matcher(blob);
            if (onlinePlayersMatcher.find()) {
                onlinePlayers = Integer.parseInt(onlinePlayersMatcher.group(1));
            }

            Matcher maxPlayersMatcher = Pattern.compile(maxPlayersRegex).matcher(blob);
            if (maxPlayersMatcher.find()) {
                maxPlayers = Integer.parseInt(maxPlayersMatcher.group(1));
            }

            Matcher locationMatcher = Pattern.compile(locationRegex).matcher(blob);
            if (locationMatcher.find()) {
                String[] countrySplit = locationMatcher.group(2).trim().split(" ");
                country = countrySplit[countrySplit.length - 1];
                //fuck this
                if (locationMatcher.group(2).contains("United")||locationMatcher.group(2).contains("Federation")) {
                    country = countrySplit[countrySplit.length - 2 ]+" "+country;
                }
                city = locationMatcher.group(3).trim().split(" ")[0];
            }



            servers.add(new ServerData(ip, version, country, city, description, onlinePlayers, maxPlayers));
        }

        return servers;
    }

    public static void loadConfig() {
        File file = new File("shodanservers/config.yml");

        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Yaml yaml = new Yaml();
                Map<String, String> config = yaml.load(reader);
                cookie = config.get("cookie");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Config file not found. Creating a new one.");
            cookie = "ADD_COOKIE_HERE";
            saveConfig();
        }
    }

    public static void saveConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("cookie", cookie);

        DumperOptions options = new DumperOptions();
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);

        try (FileWriter writer = new FileWriter("shodanservers/config.yml")) {
            yaml.dump(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
