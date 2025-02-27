package com.sparrowwallet.sparrow.net;

import com.google.common.net.HostAndPort;
import com.google.gson.Gson;
import com.sparrowwallet.sparrow.io.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public enum FeeRatesSource {
    ELECTRUM_SERVER("Server") {
        @Override
        public Map<Integer, Double> getBlockTargetFeeRates(Map<Integer, Double> defaultblockTargetFeeRates) {
            return Collections.emptyMap();
        }
    },
    MEMPOOL_SPACE("mempool.space") {
        @Override
        public Map<Integer, Double> getBlockTargetFeeRates(Map<Integer, Double> defaultblockTargetFeeRates) {
            String url = "https://mempool.space/api/v1/fees/recommended";
            return getThreeTierFeeRates(defaultblockTargetFeeRates, url);
        }
    },
    BITCOINFEES_EARN_COM("bitcoinfees.earn.com") {
        @Override
        public Map<Integer, Double> getBlockTargetFeeRates(Map<Integer, Double> defaultblockTargetFeeRates) {
            String url = "https://bitcoinfees.earn.com/api/v1/fees/recommended";
            return getThreeTierFeeRates(defaultblockTargetFeeRates, url);
        }
    };

    private static final Logger log = LoggerFactory.getLogger(FeeRatesSource.class);
    public static final int BLOCKS_IN_HALF_HOUR = 3;
    public static final int BLOCKS_IN_HOUR = 6;
    public static final int BLOCKS_IN_TWO_HOURS = 12;

    private final String name;

    FeeRatesSource(String name) {
        this.name = name;
    }

    public abstract Map<Integer, Double> getBlockTargetFeeRates(Map<Integer, Double> defaultblockTargetFeeRates);

    public String getName() {
        return name;
    }

    private static Map<Integer, Double> getThreeTierFeeRates(Map<Integer, Double> defaultblockTargetFeeRates, String url) {
        Proxy proxy = getProxy();

        Map<Integer, Double> blockTargetFeeRates = new LinkedHashMap<>();
        try(InputStream is = (proxy == null ? new URL(url).openStream() : new URL(url).openConnection(proxy).getInputStream()); Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            ThreeTierRates threeTierRates = gson.fromJson(reader, ThreeTierRates.class);
            Double lastRate = null;
            for(Integer blockTarget : defaultblockTargetFeeRates.keySet()) {
                if(blockTarget < BLOCKS_IN_HALF_HOUR) {
                    blockTargetFeeRates.put(blockTarget, threeTierRates.fastestFee);
                } else if(blockTarget < BLOCKS_IN_HOUR) {
                    blockTargetFeeRates.put(blockTarget, threeTierRates.halfHourFee);
                } else if(blockTarget < BLOCKS_IN_TWO_HOURS || defaultblockTargetFeeRates.get(blockTarget) > threeTierRates.hourFee) {
                    blockTargetFeeRates.put(blockTarget, threeTierRates.hourFee);
                } else if(threeTierRates.minimumFee != null && defaultblockTargetFeeRates.get(blockTarget) < threeTierRates.minimumFee) {
                    blockTargetFeeRates.put(blockTarget, threeTierRates.minimumFee + (threeTierRates.hourFee > threeTierRates.minimumFee ? threeTierRates.hourFee * 0.2 : 0.0));
                } else {
                    blockTargetFeeRates.put(blockTarget, defaultblockTargetFeeRates.get(blockTarget));
                }

                if(lastRate != null) {
                    blockTargetFeeRates.put(blockTarget, Math.min(lastRate, blockTargetFeeRates.get(blockTarget)));
                }
                lastRate = blockTargetFeeRates.get(blockTarget);
            }

            if(threeTierRates.minimumFee != null) {
                blockTargetFeeRates.put(Integer.MAX_VALUE, threeTierRates.minimumFee);
            }
        } catch (Exception e) {
            log.warn("Error retrieving recommended fee rates from " + url, e);
        }

        return blockTargetFeeRates;
    }

    private static Proxy getProxy() {
        Config config = Config.get();
        if(config.isUseProxy()) {
            HostAndPort proxy = HostAndPort.fromString(config.getProxyServer());
            InetSocketAddress proxyAddress = new InetSocketAddress(proxy.getHost(), proxy.getPortOrDefault(ProxyTcpOverTlsTransport.DEFAULT_PROXY_PORT));
            return new Proxy(Proxy.Type.SOCKS, proxyAddress);
        }

        return null;
    }

    @Override
    public String toString() {
        return name;
    }

    private static class ThreeTierRates {
        Double fastestFee;
        Double halfHourFee;
        Double hourFee;
        Double minimumFee;
    }
}
