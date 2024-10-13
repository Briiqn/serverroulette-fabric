package dev.briiqn.serverroulette.shodan.api;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class ShodanAPI {
    private static final String SHODAN_SEARCH_URL = "https://www.shodan.io/search";

    public String getMinecraftServers(String politoCookie, String version, String country, String city, Integer page) {
        try {
            StringBuilder queryBuilder = new StringBuilder(" +product:\"Minecraft\"");

            if (version != null && !version.isEmpty()) {
                queryBuilder.append("+version:\"").append(URLEncoder.encode(version, StandardCharsets.UTF_8.toString())).append("\"");
            }
            if (country != null && !country.isEmpty()) {
                queryBuilder.append("+country:\"").append(URLEncoder.encode(country, StandardCharsets.UTF_8.toString())).append("\"");
            }
            if (city != null && !city.isEmpty()) {
                queryBuilder.append("+city:\"").append(URLEncoder.encode(city, StandardCharsets.UTF_8.toString())).append("\"");
            }

            String urlStr = SHODAN_SEARCH_URL + "?query=" + queryBuilder.toString();
            if (page != null && page > 1) {
                urlStr += "&page=" + page;
            }
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            connection.setRequestProperty("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
            connection.setRequestProperty("accept-language", "en-US,en;q=0.9");
            connection.setRequestProperty("cache-control", "no-cache");
            if (politoCookie != null && !politoCookie.isEmpty()) {
                connection.setRequestProperty("cookie", "polito=\"" + politoCookie + "\"");
            }
            connection.setRequestProperty("dnt", "1");
            connection.setRequestProperty("pragma", "no-cache");
            connection.setRequestProperty("sec-fetch-dest", "document");
            connection.setRequestProperty("sec-fetch-mode", "navigate");
            connection.setRequestProperty("sec-fetch-site", "none");
            connection.setRequestProperty("sec-fetch-user", "?1");
            connection.setRequestProperty("sec-gpc", "1");
            connection.setRequestProperty("upgrade-insecure-requests", "1");
            connection.setRequestProperty("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                return response.toString();
            } else {
                System.out.println("GET request failed: " + responseCode);
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


}