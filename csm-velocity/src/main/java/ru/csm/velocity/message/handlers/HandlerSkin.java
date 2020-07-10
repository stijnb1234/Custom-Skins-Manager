package ru.csm.velocity.message.handlers;

import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.Player;
import ru.csm.api.network.MessageHandler;
import ru.csm.api.player.Skin;
import ru.csm.api.player.SkinModel;
import ru.csm.api.player.SkinPlayer;
import ru.csm.api.services.SkinsAPI;

public class HandlerSkin implements MessageHandler {

    private final SkinsAPI<Player> api;

    public HandlerSkin(SkinsAPI<Player> api){
        this.api = api;
    }

    @Override
    public void execute(JsonObject json) {
        SkinPlayer player = api.getPlayer(json.get("player").getAsString());

        if (player != null){
            String action = json.get("action").getAsString();

            switch (action){
                case "set":{
                    Skin skin = new Skin();
                    skin.setValue(json.get("skin_value").getAsString());
                    skin.setSignature(json.get("skin_signature").getAsString());
                    api.setCustomSkin(player, skin);
                    break;
                }
                case "reset":{
                    api.resetSkin(player);
                    break;
                }
                case "name":{
                    String name = json.get("name").getAsString();
                    api.setSkinFromName(player, name);
                    break;
                }
                case "image":{
                    String url = json.get("url").getAsString();
                    SkinModel model = SkinModel.valueOf(json.get("model").getAsString());
                    api.setSkinFromImage(player, url, model);
                    break;
                }
            }
        }
    }
}
