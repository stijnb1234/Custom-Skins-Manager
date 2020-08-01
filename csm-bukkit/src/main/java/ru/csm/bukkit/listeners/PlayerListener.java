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

package ru.csm.bukkit.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import ru.csm.api.services.SkinsAPI;
import ru.csm.api.player.SkinPlayer;
import ru.csm.bukkit.util.BukkitTasks;

public class PlayerListener implements Listener {

    private final SkinsAPI<Player> api;

    public PlayerListener(SkinsAPI<Player> api) {
        this.api = api;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event){
        BukkitTasks.runTaskAsync(()->{
            SkinPlayer player = api.loadPlayer(event.getPlayer().getUniqueId(), event.getPlayer().getName());

            if (player == null){
                player = api.buildPlayer(event.getPlayer().getUniqueId(), event.getPlayer().getName());
                api.createNewPlayer(player);
            }

            api.addPlayer(player);

            player.applySkin();

            if(!event.getPlayer().isDead()){
                player.refreshSkin();
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        api.removePlayer(event.getPlayer().getUniqueId());
    }
}
