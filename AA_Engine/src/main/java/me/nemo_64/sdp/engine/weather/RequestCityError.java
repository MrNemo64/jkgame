package me.nemo_64.sdp.engine.weather;

public interface RequestCityError {

    static RequestCityError ofThrowable(Throwable t) {
        return t::getMessage;
    }

    String message();

}
