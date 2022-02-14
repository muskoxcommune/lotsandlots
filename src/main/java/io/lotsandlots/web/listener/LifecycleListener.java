package io.lotsandlots.web.listener;

import com.typesafe.config.Config;
import io.lotsandlots.etrade.EtradeBuyOrderController;
import io.lotsandlots.etrade.EtradeOrdersDataFetcher;
import io.lotsandlots.etrade.EtradePortfolioDataFetcher;
import io.lotsandlots.etrade.EtradeSellOrderController;
import io.lotsandlots.etrade.rest.EtradeRestTemplateFactory;
import io.lotsandlots.util.ConfigWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.awt.Desktop;
import java.net.URI;

/**
 * Listens to ServletContext lifecycle events and initiates startup behavior.
 */
public class LifecycleListener implements ServletContextListener {

    private static final Logger LOG = LoggerFactory.getLogger(LifecycleListener.class);
    private static final Config CONFIG = ConfigWrapper.getConfig();

    private static LifecycleListener LISTENER;

    private EtradeOrdersDataFetcher ordersDataFetcher;
    private EtradePortfolioDataFetcher portfolioDataFetcher;

    private EtradeBuyOrderController buyOrderController;
    private EtradeSellOrderController sellOrderController;

    @Override
    public void contextInitialized(ServletContextEvent contextEvent) {
        LISTENER = this;
        Config etradeConfig = CONFIG.getConfig("etrade");
        if (etradeConfig != null) {
            try {
                // Until EtradeRestTemplateFactory is initialized, we won't be able to send requests to E*Trade.
                EtradeRestTemplateFactory.init();
                LOG.info("Initialized EtradeRestTemplateFactory");
                LOG.info("Go to http://localhost:5000/etrade/authorize if a browser window doesn't open automatically");
                if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    // Send to authorize URL to begin auth flow.
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

    public static LifecycleListener getListener() {
        return LISTENER;
    }

    public EtradeBuyOrderController getBuyOrderController() {
        return buyOrderController;
    }
    public void setBuyOrderController(EtradeBuyOrderController buyOrderController) {
        this.buyOrderController = buyOrderController;
    }

    public EtradeOrdersDataFetcher getOrdersDataFetcher() {
        return ordersDataFetcher;
    }
    public void setOrdersDataFetcher(EtradeOrdersDataFetcher ordersDataFetcher) {
        this.ordersDataFetcher = ordersDataFetcher;
    }

    public EtradePortfolioDataFetcher getPortfolioDataFetcher() {
        return portfolioDataFetcher;
    }
    public void setPortfolioDataFetcher(EtradePortfolioDataFetcher portfolioDataFetcher) {
        this.portfolioDataFetcher = portfolioDataFetcher;
    }

    public EtradeSellOrderController getSellOrderController() {
        return sellOrderController;
    }
    public void setSellOrderController(EtradeSellOrderController sellOrderController) {
        this.sellOrderController = sellOrderController;
    }
}
