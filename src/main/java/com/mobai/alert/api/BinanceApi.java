package com.mobai.alert.api;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.mobai.alert.dto.BinanceKlineDTO;
import com.mobai.alert.dto.BinanceSymbolsDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Binance REST API 访问封装，负责拉取交易对列表和 K 线数据。
 */
@Component
public class BinanceApi {

    private static final String KLINE_BASE_URL = "https://fapi.binance.com/fapi/v1/klines";
    private static final String SYMBOLS_BASE_URL = "https://fapi.binance.com/fapi/v1/exchangeInfo";

    @Autowired
    private RestTemplate restTemplate;

    private String apiKey = "6JyypvY7m4zramFJkkWbgy";

    /**
     * 查询指定交易对的 K 线数据。
     *
     * @param reqDTO 查询参数
     * @return K 线列表
     */
    public List<BinanceKlineDTO> listKline(BinanceKlineDTO reqDTO) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        String url = KLINE_BASE_URL + "?symbol=" + reqDTO.getSymbol();
        url = url + "&interval=" + reqDTO.getInterval();
        url = url + "&limit=" + reqDTO.getLimit();
        if (reqDTO.getStartTime() != null) {
            url = url + "&startTime=" + reqDTO.getStartTime();
        }
        if (reqDTO.getEndTime() != null) {
            url = url + "&endTime=" + reqDTO.getEndTime();
        }

        List<BinanceKlineDTO> binanceKlineDTOS = new ArrayList<>();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String body = response.getBody();
            JSONArray objects = JSON.parseArray(body);
            for (Object object : objects) {
                BinanceKlineDTO tmpDTO = new BinanceKlineDTO();
                JSONArray tmpArr = JSON.parseArray(object.toString());
                tmpDTO.setSymbol(reqDTO.getSymbol());
                tmpDTO.setStartTime(Long.parseLong(tmpArr.get(0).toString()));
                tmpDTO.setHigh(tmpArr.get(2).toString());
                tmpDTO.setLow(tmpArr.get(3).toString());
                tmpDTO.setOpen(tmpArr.get(1).toString());
                tmpDTO.setClose(tmpArr.get(4).toString());
                tmpDTO.setAmount(tmpArr.get(5).toString());
                tmpDTO.setVolume(tmpArr.get(7).toString());
                tmpDTO.setEndTime(Long.parseLong(tmpArr.get(6).toString()));
                binanceKlineDTOS.add(tmpDTO);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return binanceKlineDTOS;
    }

    /**
     * 查询 Binance 期货交易对列表。
     *
     * @return 交易对列表响应
     */
    public BinanceSymbolsDTO listSymbols() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-MBX-APIKEY", apiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(SYMBOLS_BASE_URL, HttpMethod.GET, entity, String.class);
            System.out.println("listSymbols res:" + response.getBody());
            return JSON.parseObject(response.getBody(), BinanceSymbolsDTO.class);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new BinanceSymbolsDTO();
    }

}
