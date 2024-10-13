package dev.briiqn.serverroulette.shodan.api;

public  class ServerData {
    public   String ip;
    public   String version;
    public     String country;
    public  String city;
    public String description;
       public int onlinePlayers;
    public  int maxPlayers;

        public ServerData(String ip, String version, String country, String city, String description, int onlinePlayers, int maxPlayers) {
            this.ip = ip;
            this.version = version;
            this.country = country;
            this.city = city;
            this.description = description;
            this.onlinePlayers = onlinePlayers;
            this.maxPlayers = maxPlayers;
        }

        @Override
        public String toString() {
            return String.format("IP: %s, Version: %s, Country: %s, City: %s, Description: %s, Online Players: %d, Max Players: %d",
                    ip, version, country, city, description, onlinePlayers, maxPlayers);
        }
    }