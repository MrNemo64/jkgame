package me.nemo_64.sdp.player.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import me.nemo_64.sdp.player.CreateAccountManager;
import me.nemo_64.sdp.player.util.ConfigurationEntry;
import me.nemo_64.sdp.player.util.PlayerData;
import me.nemo_64.sdp.utilities.configuration.Config;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class RESTCreateAccountManager extends CreateAccountManager {
    @Override
    public void createNewAccount(String registryIp, int registryPort) {
        JsonObject data = PlayerData.askForData().asJson();
        LOGGER.info("Sending registry " + data);
        String url = Config.getString(ConfigurationEntry.REGISTRY_HTTP_IP);
        try {
            var result = HttpClientInstance.get().put(url, data).get();
            if (result.isError()) {
                System.out.println("Could not create account. See logs for more information.");
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
