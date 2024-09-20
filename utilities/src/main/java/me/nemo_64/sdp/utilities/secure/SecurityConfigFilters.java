package me.nemo_64.sdp.utilities.secure;

import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class SecurityConfigFilters {

    public static boolean isValidCipherAlgorithm(String algorithm) {
        return listCipherAlgorithms().contains(algorithm);
    }

    public static Set<String> listCipherAlgorithms() {
        return Arrays.stream(Security.getProviders())
                .flatMap(provider -> provider.getServices().stream())
                .filter(service -> "Cipher".equals(service.getType()))
                .map(Provider.Service::getAlgorithm)
                .collect(Collectors.toSet());
    }

}
