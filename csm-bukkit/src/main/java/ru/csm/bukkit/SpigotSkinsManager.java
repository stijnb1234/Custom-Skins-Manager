/*
 * Custom Skins Manager
 * Copyright (C) 2020  Nanit
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ru.csm.bukkit;

import napi.configurate.Configuration;
import napi.configurate.serializing.NodeSerializers;
import napi.configurate.source.ConfigSources;
import napi.configurate.yaml.YamlConfiguration;
import napi.reflect.BukkitReflect;
import napi.util.LibLoader;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import ru.csm.api.Dependency;
import ru.csm.api.logging.JULHandler;
import ru.csm.api.network.Channels;
import ru.csm.api.player.Skin;
import ru.csm.api.services.SkinHash;
import ru.csm.api.storage.*;
import ru.csm.api.logging.Logger;
import ru.csm.api.upload.Profile;
import ru.csm.bukkit.commands.Commands;
import ru.csm.bukkit.nms.Holograms;
import ru.csm.bukkit.nms.Npcs;
import ru.csm.bukkit.nms.SkinHandlers;
import ru.csm.bukkit.npc.inject.NpcPacketHandler;
import ru.csm.api.services.SkinsAPI;
import ru.csm.bukkit.listeners.InventoryListener;
import ru.csm.bukkit.listeners.NpcClickListener;
import ru.csm.bukkit.listeners.PlayerListener;
import ru.csm.bukkit.listeners.RespawnListener;
import ru.csm.bukkit.menu.item.Item;
import ru.csm.bukkit.messages.PluginMessageReceiver;
import ru.csm.bukkit.messages.PluginMessageSender;
import ru.csm.bukkit.messages.handlers.HandlerMenu;
import ru.csm.bukkit.messages.handlers.HandlerPreview;
import ru.csm.bukkit.messages.handlers.HandlerSkin;
import ru.csm.bukkit.messages.handlers.HandlerSkull;
import ru.csm.bukkit.placeholders.Placeholders;
import ru.csm.bukkit.services.ProxySkinsAPI;
import ru.csm.bukkit.services.SpigotSkinsAPI;
import ru.csm.bukkit.services.MenuManager;
import ru.csm.bukkit.util.BukkitTasks;
import ru.csm.api.utils.FileUtil;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;

public class SpigotSkinsManager extends JavaPlugin {

    private Database database;

    @Override
    public void onLoad() {
        Logger.set(new JULHandler(getLogger()));

        try {
            // Check for Bukkit's library loader
            Class.forName("org.bukkit.plugin.java.LibraryLoader");
        } catch (ClassNotFoundException cnfe) {
            // Load libraries if Spigot < 1.16.5
            Path libsFolder = Paths.get(getDataFolder().toString(), "libs");

            try {
                LibLoader libLoader = new LibLoader(this, libsFolder);

                libLoader.download(Dependency.H2.getName(), Dependency.H2.getUrl());
                libLoader.download(Dependency.DBCP.getName(), Dependency.DBCP.getUrl());
                libLoader.load(libsFolder);
            } catch (Exception e){
                Logger.severe("Cannot load library: " + e.getMessage());
            }
        }
    }

    @Override
    public void onEnable(){
        try{
            new Metrics(this, 7375);

            registerSerializers();

            String packageName = getServer().getClass().getPackage().getName();
            String version = packageName.substring(packageName.lastIndexOf('.') + 1);

            BukkitReflect.init(getServer());
            SkinHandlers.init(version);
            Holograms.init(version);
            Npcs.init(version);
            NpcPacketHandler.initClasses();
            BukkitTasks.setPlugin(this);

            Configuration configurationFile = YamlConfiguration.builder()
                    .source(ConfigSources.resource("/bukkit/config.yml", this).copyTo(getDataFolder().toPath()))
                    .build();

            SkinsConfig config = new SkinsConfig(this, configurationFile);

            configurationFile.reload();
            config.load(getDataFolder().toPath());

            MenuManager menuManager = new MenuManager(config.getLanguage());

            try {
                setupDatabase(config);
            } catch (SQLException e) {
                Logger.severe("Cannot connect to SQL database: %s", e.getMessage());
                getPluginLoader().disablePlugin(this);
                return;
            }

            SkinsAPI<Player> api = new SpigotSkinsAPI(database, config, config.getLanguage(), menuManager);

            BukkitTasks.runTaskTimerAsync(SkinHash::clean, 0, 900); // 30 sec

            getServer().getPluginManager().registerEvents(new PlayerListener(api), this);
            getServer().getServicesManager().register(SkinsAPI.class, api, this, ServicePriority.Normal);

            getServer().getPluginManager().registerEvents(new InventoryListener(), this);
            getServer().getPluginManager().registerEvents(new RespawnListener(api), this);
            getServer().getPluginManager().registerEvents(new NpcClickListener(api, menuManager), this);

            getServer().getServicesManager().register(SkinsAPI.class, api, this, ServicePriority.Normal);

            if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")){
                Placeholders.init();
            }

            Commands.init(this, api);
        } catch (Exception e){
            Logger.severe("Cannot enable plugin: " + e.getMessage());
        }
    }

    @Override
    public void onDisable(){
        if(database != null){
            database.closeConnection();
        }
    }

    private void registerSerializers(){
        NodeSerializers.register(Profile.class, new Profile.Serializer());
        NodeSerializers.register(Skin.class, new Skin.Serializer());
        NodeSerializers.register(Item.class, new Item.Serializer());
    }

    private void setupDatabase(SkinsConfig conf) throws SQLException {
        String type = conf.getDbType().toLowerCase();

        switch (type) {
            case "h2": {
                Path path = Paths.get(getDataFolder().getAbsolutePath(), "skins");
                this.database = new H2Database(path, conf.getDbUser(), conf.getDbPassword());
                break;
            }
            case "sqlite": {
                String path = getDataFolder().getAbsolutePath();
                this.database = new SQLiteDatabase(path, conf.getDbName(), conf.getDbUser(), conf.getDbPassword());
                break;
            }
            case "mysql": {
                String host = conf.getDbHost();
                int port = conf.getDbPort();
                String dbname = conf.getDbName();
                String user = conf.getDbUser();
                String password = conf.getDbPassword();
                this.database = new MySQLDatabase(host, port, dbname, user, password);
                break;
            }
            default:
                throw new SQLException("Undefined database type: " + type);
        }

        this.database.executeSQL(FileUtil.readResourceContent("/tables/" + type + "/skins.sql"));
    }
}
