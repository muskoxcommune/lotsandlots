package io.lotsandlots.util;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class ConfigWrapper {

    private static final Config CONFIG = ConfigFactory.load();

    public static Config getConfig() {
        return CONFIG;
    }
}
