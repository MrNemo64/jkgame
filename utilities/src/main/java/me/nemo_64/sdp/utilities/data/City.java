package me.nemo_64.sdp.utilities.data;

import com.google.gson.JsonObject;
import me.nemo_64.sdp.utilities.JsonUtil;

import java.util.Optional;

public record City(String name, double temperature) {

    public static final String UNKNOWN_CITY_NAME = "UNKNOWN";
    public static final double UNKNOWN_CITY_TEMPERATURE = 0;
    public static final City UNKNOWN_CITY = new City(City.UNKNOWN_CITY_NAME, City.UNKNOWN_CITY_TEMPERATURE);

    public static Optional<City> deserialize(String serialized) {
        if (serialized == null)
            return Optional.empty();
        String[] parts = serialized.split(";");
        if (parts.length != 2)
            return Optional.empty();
        try {
            String cityName = parts[0];
            double cityTemperature = Double.parseDouble(parts[1]);
            if (cityTemperature == City.UNKNOWN_CITY_TEMPERATURE && cityName.equals(City.UNKNOWN_CITY_NAME))
                return Optional.of(UNKNOWN_CITY);
            return Optional.of(new City(cityName, cityTemperature));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static Optional<City> fromJson(JsonObject json) {
        var name = JsonUtil.getString(json, "name");
        if (name.isEmpty())
            return Optional.empty();
        var temp = JsonUtil.getNumber(json, "temperature");
        if (temp.isEmpty())
            return Optional.empty();
        return Optional.of(new City(name.get(), temp.get().doubleValue()));
    }

    public String prettyPrint() {
        return name + " " + temperature;
    }

    public String serialize() {
        return name + ";" + temperature;
    }

    public boolean isValid() {
        return !equals(City.UNKNOWN_CITY);
    }

    public JsonObject asJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name());
        json.addProperty("temperature", temperature());
        return json;
    }

    @Override
    public int hashCode() {
        return name().hashCode();
    }
}
