package io.lotsandlots.web.listener;

import com.typesafe.config.Config;
import io.lotsandlots.etrade.EtradeRestTemplateFactory;
import io.lotsandlots.util.ConfigWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class LifecycleListener implements ServletContextListener {

    private static final Logger LOG = LoggerFactory.getLogger(LifecycleListener.class);
    private static final Config CONFIG = ConfigWrapper.getConfig();

    @Override
    public void contextInitialized(ServletContextEvent contextEvent) {
        Config etradeConfig = CONFIG.getConfig("etrade");
        if (etradeConfig != null) {
            try {
                EtradeRestTemplateFactory.init();
                LOG.info("Initialized EtradeRestTemplateFactory");
            } catch (Exception e) {
                LOG.error("Failed to initialize EtradeRestTemplateFactory");
            }
        }
        LOG.info("Servlet context initialized");
    }

    @Override
    public void contextDestroyed(ServletContextEvent contextEvent) {
        LOG.info("Servlet context destroyed");
    }
}
