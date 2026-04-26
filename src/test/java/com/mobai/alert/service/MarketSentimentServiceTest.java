package com.mobai.alert.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MarketSentimentServiceTest {

    @Test
    void shouldInsertCompositeMarketSentimentBeforeTrailingLink() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        MarketSentimentService service = new MarketSentimentService(restTemplate);

        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "cacheMs", 300_000L);
        ReflectionTestUtils.setField(service, "cryptopanicApiKey", "");
        ReflectionTestUtils.setField(service, "cryptopanicPages", 3);
        ReflectionTestUtils.setField(service, "minMentions", 2);
        ReflectionTestUtils.setField(service, "cryptopanicWeight", 0.45d);
        ReflectionTestUtils.setField(service, "gainersWeight", 0.25d);
        ReflectionTestUtils.setField(service, "coingeckoWeight", 0.15d);
        ReflectionTestUtils.setField(service, "fearGreedWeight", 0.15d);
        ReflectionTestUtils.setField(service, "min24hVolumeUsdt", 50_000_000d);

        server.expect(once(), requestTo("https://www.binance.info/en/square/hashtag/btcusdt"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("<html><body>12.5M views 4.8K Discussing</body></html>", MediaType.TEXT_HTML));
        server.expect(once(), requestTo("https://www.reddit.com/r/CryptoCurrency/hot.json?limit=100"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "children": [
                              {"data": {"title": "BTC breakout setup", "selftext": ""}},
                              {"data": {"title": "Alt rotation", "selftext": "$BTC funding still strong"}}
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.alternative.me/fng/?limit=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"data":[{"value":"63","value_classification":"Greed"}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.coingecko.com/api/v3/search/trending"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "coins": [
                            {"item": {"symbol": "BTC"}},
                            {"item": {"symbol": "ETH"}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://fapi.binance.com/fapi/v1/ticker/24hr"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {"symbol":"BTCUSDT","quoteVolume":"120000000","priceChangePercent":"12.5"},
                          {"symbol":"ETHUSDT","quoteVolume":"110000000","priceChangePercent":"8.0"}
                        ]
                        """, MediaType.APPLICATION_JSON));

        String result = service.enrichBody("BTCUSDT", "信号正文\n[查看图表](https://example.com/chart)");

        server.verify();

        assertTrue(result.contains("市场热度：74/100（高）"));
        assertTrue(result.contains("Square热议 4.8K"));
        assertTrue(result.contains("Reddit提及 2"));
        assertTrue(result.contains("CoinGecko #1"));
        assertTrue(result.contains("涨幅榜 #1"));
        assertTrue(result.contains("F&G 63"));
        assertTrue(result.indexOf("市场热度：") < result.indexOf("[查看图表]"));
    }
}
