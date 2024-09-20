package me.nemo_64.sdp.player.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import me.nemo_64.sdp.player.ModifyAccountManager;
import me.nemo_64.sdp.player.util.ConfigurationEntry;
import me.nemo_64.sdp.player.util.PlayerData;
import me.nemo_64.sdp.utilities.configuration.Config;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class RESTModifyAccountManager extends ModifyAccountManager {

    @Override
    public void modifyAccount(String registryIp, int registryPort) {
        System.out.println("Input account credentials");
        PlayerData target = PlayerData.askForCredentials();
        System.out.println("Input new account values");
        JsonObject newValues = PlayerData.askForData().asJson();
        JsonObject data = new JsonObject();
        data.addProperty("target", target.alias());
        data.addProperty("password", target.password());
        data.add("values", newValues);
        LOGGER.info("Sending registry " + data);
        String url = Config.getString(ConfigurationEntry.REGISTRY_HTTP_IP);
        try {
            var result = HttpClientInstance.get().post(url, data).get();
            if (result.isError()) {
                System.out.println("Could not update account. See logs for more information.");
                LOGGER.warning(result.error());
                return;
            }
            Optional<String> message = result.value()
                    .filter(JsonElement::isJsonPrimitive)
                    .map(JsonElement::getAsJsonPrimitive)
                    .filter(JsonPrimitive::isString)
                    .map(JsonPrimitive::getAsString);
            if (message.isPresent()) {
                System.out.println(message);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
