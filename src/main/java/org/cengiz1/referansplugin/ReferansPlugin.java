package org.cengiz1.referansplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ReferansPlugin extends JavaPlugin implements CommandExecutor {

    private final Map<String, String> referredPlayers = new HashMap<>();
    private String rewardCommand;

    private Connection connection;

    private int saveIntervalMinutes;

    public ReferansPlugin() {



    }


    @Override
    public void onEnable() {
        saveDefaultConfig();
        rewardCommand = getConfig().getString("reward-command", "");
        Objects.requireNonNull(getCommand("referansol")).setExecutor(this);
        Objects.requireNonNull(getCommand("referanslarım")).setExecutor(this);
        setupDatabase();
        startDatabaseSaveTask();
        saveIntervalMinutes = getConfig().getInt("otomatik-kayit", 2);
        Bukkit.getLogger().info("[ReferansPlugin] aktif!");
    }

    @Override
    public void onDisable() {
        saveReferredPlayers();
        closeDatabase();
        Bukkit.getLogger().info("[ReferansPlugin] Devre Dışı!");
    }
    private boolean isFirstRun = true; // İlk çalışma için kontrol değişkeni

    private void startDatabaseSaveTask() {
        int saveIntervalTicks = saveIntervalMinutes * 1200; // Dakika cinsinden kaydetme aralığı
        new BukkitRunnable() {
            @Override
            public void run() {
                saveReferredPlayers(); // Veritabanını kaydet

                if (isFirstRun) {
                    Bukkit.getLogger().info(logColoredMessage("&7[&eReferansPlugin&7] veri tabanı aktif")); // Sadece ilk çalışmada mesajı yazdır
                    isFirstRun = false; // İlk çalışma gerçekleşti, bir daha yazdırma
                }
            }
        }.runTaskTimerAsynchronously(this, 0, saveIntervalTicks);
    }



    private void setupDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder() + "/database.db");
            try (PreparedStatement stmt = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS referrals (" +
                            "referrer TEXT NOT NULL," +
                            "referee TEXT NOT NULL);")) {
                stmt.execute();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void closeDatabase() {
        if (connection != null) {
            try {
                connection.close();
                Bukkit.getLogger().info(logColoredMessage("&7[&eReferansPlugin&7] veri tabanı DevreDışı")); // Veritabanı başarıyla oluşturulduğunda mesaj yazdırılıyor
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
    public String logColoredMessage(String message) {
        String coloredMessage = ChatColor.translateAlternateColorCodes('&', message);
        Bukkit.getLogger().info(coloredMessage);
        return coloredMessage;
    }
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(logColoredMessage("&7[&eReferansPlugin&7] Bu komut sadece oyuncular tarafından kullanılabilir."));
            return true;
        }

        Player referrer = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("referansol")) {
            if (args.length != 1) {
                referrer.sendMessage(logColoredMessage("&7[&eReferansPlugin&7] Kullanım: /referansol <oyuncuAdı>"));
                return true;
            }

            String refereeName = args[0];
            Player referee = referrer.getServer().getPlayerExact(refereeName);

            if (referee == null) {
                referrer.sendMessage(logColoredMessage("&7[&eReferansPlugin&7] &cBu oyuncu çevrimdışı veya hatalı bir oyuncu adı."));
                return true;
            }

            if (referrer.equals(referee)) {
                referrer.sendMessage(logColoredMessage("&7[&eReferansPlugin&7] &cKendinize referans olamazsınız."));
                return true;
            }

            if (referredPlayers.containsKey(referrer.getName())) {
                referrer.sendMessage(logColoredMessage("&7[&eReferansPlugin&7] &cZaten birine referans oldunuz."));
                return true;
            }

            if (referredPlayers.containsValue(referrer.getName())) {
                referrer.sendMessage(logColoredMessage("&7[&eReferansPlugin&7] &cBu oyuncu zaten birine referans oldu."));
                return true;
            }

            referredPlayers.put(referrer.getName(), referee.getName());

            if (!rewardCommand.isEmpty()) {
                // Referrer'a ödül ver
                String referrerRewardCommand = rewardCommand.replace("<player>", referrer.getName());
                getServer().dispatchCommand(getServer().getConsoleSender(), referrerRewardCommand);

                // Referee'ye ödül ver
                String refereeRewardCommand = rewardCommand.replace("<player>", referee.getName());
                getServer().dispatchCommand(getServer().getConsoleSender(), refereeRewardCommand);
            }

            referrer.sendMessage(logColoredMessage("&7[&eReferansPlugin&7] &aBaşarıyla " + referee.getName() + " &aoyuncusuna referans oldunuz ve ödülünüz verildi!"));
            return true;
        } else if (cmd.getName().equalsIgnoreCase("referanslarım")) {
            String playerName = referrer.getName();
            int count = 0;

            referrer.sendMessage(logColoredMessage("&7[&eReferansPlugin&7] &eSize referans olan oyuncular:"));

            for (Map.Entry<String, String> entry : referredPlayers.entrySet()) {
                if (entry.getValue().equals(playerName)) {
                    referrer.sendMessage(logColoredMessage("&7- " + entry.getKey()));
                    count++;
                }
            }

            if (count == 0) {
                referrer.sendMessage(logColoredMessage("&7[&eReferansPlugin&7] Henüz size kimse referans olmadı"));
            }

        }

        return false;
    }

    private void saveReferredPlayers() {
        try {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "INSERT INTO referrals (referrer, referee) VALUES (?, ?);")) {
                for (Map.Entry<String, String> entry : referredPlayers.entrySet()) {
                    stmt.setString(1, entry.getKey());
                    stmt.setString(2, entry.getValue());
                    stmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
