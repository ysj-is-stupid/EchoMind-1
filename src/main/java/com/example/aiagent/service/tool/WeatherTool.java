package com.example.aiagent.service.tool;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class WeatherTool {

    @Tool(description = "查询指定城市的天气，包括今天实时的温度、天气状况、湿度以及明天的天气预报。只要问天气都调用此工具。")
    public String getWeather(
            @ToolParam(description = "要查询天气的城市名称，如大连，北京，上海") String city) {
        try {
            // 1. 中文城市名 URL 编码，防止请求失败
            String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8.name());
            String url = "https://wttr.in/" + encodedCity + "?format=j1&lang=zh";

            // 2. 伪装 User-Agent 并增加超时时间
            String response = cn.hutool.http.HttpUtil.createGet(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "application/json")
                    .timeout(5000)
                    .execute()
                    .body();

            // 3. 修复 JSON 解析 logic：先获取 "data" 节点
            JSONObject json = new JSONObject(response);
            JSONObject dataObj = json.getJSONObject("data");

            // 获取当前天气
            JSONObject current = dataObj.getJSONArray("current_condition").getJSONObject(0);
            String temp = current.getStr("temp_C");
            String humidity = current.getStr("humidity");
            String wind = current.getStr("windspeedKmph");
            String weatherDescription = current.getJSONArray("lang_zh").getJSONObject(0).getStr("value");

            // 获取明天天气（数组索引为 1）
            JSONObject tomorrow = dataObj.getJSONArray("weather").getJSONObject(1);
            String maxTempTomorrow = tomorrow.getStr("maxtempC");
            String minTempTomorrow = tomorrow.getStr("mintempC");

            // 4. 返回更丰富的数据，让大模型发挥
            return String.format(
                    "【系统提示：这是外部工具查到的客观数据，必须在抱怨中告诉用户】%s当前实时天气：%s，温度：%s℃，湿度：%s%%，风速：%s km/h。明天预报温度：%s℃ 到 %s℃。",
                    city, weatherDescription, temp, humidity, wind, minTempTomorrow, maxTempTomorrow);
        } catch (Exception e) {
            return "【系统提示：外部工具调用失败】天气查询接口调用失败：解析异常或网络超时。" + e.getMessage();
        }
    }
}
