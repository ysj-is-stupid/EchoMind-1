package com.example.aiagent.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.aiagent.model.ConversationPair;
import com.example.aiagent.model.ParsedMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * QQ 聊天记录解析器，支持按时间窗口切片并保留上下文
 */
@Service
public class ChatLogParser {

    /** 同一人连续消息的最大合并间隔：2 分钟 */
    private static final long MERGE_INTERVAL_MS = 2 * 60 * 1000;

    /** 事实提取的上下文窗口大小 */
    private static final int CONTEXT_WINDOW_SIZE = 5;

    /** 两条消息间隔超过 30 分钟视为新场景 */
    private static final long SCENE_TIMEOUT_MS = 30 * 60 * 1000;

    /** 匹配图片/视频等媒体占位符 */
    private static final Pattern MEDIA_PATTERN = Pattern.compile(
            "^\\[(?:图片|视频|语音|文件)[:：].*]$");

    /** 从 JSON 提取消息，过滤撤回和系统消息 */
    private List<ParsedMessage> extractMessages(String jsonStr) {
        JSONObject root = JSONUtil.parseObj(jsonStr);
        JSONArray messages = root.getJSONArray("messages");

        List<ParsedMessage> result = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            JSONObject msg = messages.getJSONObject(i);

            // 跳过撤回和系统消息
            if (msg.getBool("recalled", false) || msg.getBool("system", false)) {
                continue;
            }

            ParsedMessage parsed = new ParsedMessage();
            parsed.setSenderName(msg.getJSONObject("sender").getStr("name"));
            parsed.setSenderUid(msg.getJSONObject("sender").getStr("uid"));
            parsed.setContent(msg.getJSONObject("content").getStr("text", ""));
            parsed.setTimestamp(msg.getLong("timestamp"));
            parsed.setTime(msg.getStr("time"));
            result.add(parsed);
        }
        return result;
    }

    /** 过滤空内容、媒体占位符和 JSON 卡片消息 */
    private List<ParsedMessage> filterMessages(List<ParsedMessage> messages) {
        return messages.stream()
                .filter(msg -> {
                    String text = msg.getContent().trim();
                    if (text.isEmpty())
                        return false;
                    if (MEDIA_PATTERN.matcher(text).matches())
                        return false;
                    if (text.startsWith("{") || text.startsWith("[{"))
                        return false;
                    return true;
                })
                .toList();
    }

    /** 合并同一人 2 分钟内的连续消息 */
    private List<ParsedMessage> mergeConsecutiveMessages(List<ParsedMessage> messages) {
        if (messages.isEmpty())
            return messages;

        List<ParsedMessage> merged = new ArrayList<>();
        ParsedMessage current = copyMessage(messages.get(0));

        for (int i = 1; i < messages.size(); i++) {
            ParsedMessage next = messages.get(i);
            boolean sameSender = current.getSenderUid().equals(next.getSenderUid());
            boolean withinInterval = (next.getTimestamp() - current.getTimestamp()) <= MERGE_INTERVAL_MS;

            if (sameSender && withinInterval) {
                current.setContent(current.getContent() + " " + next.getContent());
            } else {
                merged.add(current);
                current = copyMessage(next);
            }
        }
        merged.add(current);
        return merged;
    }

    /**
     * 将聊天记录解析为场景列表：先按 15 分钟停顿切 Session，再按 40 条（重叠 5 条）切 Scene
     */
    public List<List<ParsedMessage>> parseToScenes(String jsonStr) {
        List<ParsedMessage> raw = extractMessages(jsonStr);
        List<ParsedMessage> filtered = filterMessages(raw);
        List<ParsedMessage> merged = mergeConsecutiveMessages(filtered);

        List<List<ParsedMessage>> sessions = splitIntoSessions(merged);
        List<List<ParsedMessage>> allScenes = new ArrayList<>();

        for (List<ParsedMessage> session : sessions) {
            allScenes.addAll(splitIntoScenes(session));
        }
        return allScenes;
    }

    /** 按 15 分钟停顿切分 Session */
    private List<List<ParsedMessage>> splitIntoSessions(List<ParsedMessage> messages) {
        List<List<ParsedMessage>> sessions = new ArrayList<>();
        if (messages.isEmpty())
            return sessions;

        List<ParsedMessage> currentSession = new ArrayList<>();
        currentSession.add(messages.get(0));

        for (int i = 1; i < messages.size(); i++) {
            ParsedMessage prev = messages.get(i - 1);
            ParsedMessage curr = messages.get(i);

            // 间隔超过 15 分钟则另起一个 Session
            if (curr.getTimestamp() - prev.getTimestamp() > 15 * 60 * 1000) {
                sessions.add(currentSession);
                currentSession = new ArrayList<>();
            }
            currentSession.add(curr);
        }
        sessions.add(currentSession);
        return sessions;
    }

    /** Session 内部按 40 条（重叠 5 条）切分 Scene */
    private List<List<ParsedMessage>> splitIntoScenes(List<ParsedMessage> session) {
        List<List<ParsedMessage>> scenes = new ArrayList<>();
        int size = session.size();
        int limit = 40;
        int overlap = 5;

        if (size <= limit) {
            scenes.add(session);
            return scenes;
        }

        for (int start = 0; start < size; start += (limit - overlap)) {
            int end = Math.min(start + limit, size);
            scenes.add(new ArrayList<>(session.subList(start, end)));
            if (end == size)
                break;
        }
        return scenes;
    }

    private ParsedMessage copyMessage(ParsedMessage src) {
        ParsedMessage copy = new ParsedMessage();
        copy.setSenderName(src.getSenderName());
        copy.setSenderUid(src.getSenderUid());
        copy.setContent(src.getContent());
        copy.setTimestamp(src.getTimestamp());
        copy.setTime(src.getTime());
        return copy;
    }
}
