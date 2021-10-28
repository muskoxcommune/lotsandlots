package io.lotsandlots.web.listener;

import com.typesafe.config.Config;
import io.lotsandlots.etrade.EtradeBuyOrderCreator;
import io.lotsandlots.etrade.EtradeOrdersDataFetcher;
import io.lotsandlots.etrade.EtradeSellOrderCreator;
import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;
import io.lotsandlots.util.ConfigWrapper;
import io.lotsandlots.etrade.EtradePortfolioDataFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.awt.Desktop;
import java.net.URI;

public class LifecycleListener implements ServletContextListener {

    private static final Logger LOG = LoggerFactory.getLogger(LifecycleListener.class);
    private static final Config CONFIG = ConfigWrapper.getConfig();

    @Override
    public void contextInitialized(ServletContextEvent contextEvent) {
        Config etradeConfig = CONFIG.getConfig("etrade");
        if (etradeConfig != null) {
            try {
                EtradeRestTemplateFactory.init();
                EtradeOrdersDataFetcher.init();
                EtradePortfolioDataFetcher.init();
                EtradePortfolioDataFetcher.addSymbolToLotsIndexPutHandler(new EtradeBuyOrderCreator());
                EtradePortfolioDataFetcher.addSymbolToLotsIndexPutHandler(new EtradeSellOrderCreator());
                LOG.info("Initialized EtradeRestTemplateFactory");
                if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI("http://localhost:5000/etrade/authorize"));
                }
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
