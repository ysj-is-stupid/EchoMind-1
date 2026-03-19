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
 * QQ 聊天记录解析器（v2 - 带上下文场景）
 *
 * 改进点：
 * 1. 提取对话对时，带上前面 N 条消息作为上下文（滑动窗口）
 * 2. 同一个场景内的对话能保留完整语境
 * 3. 用于后续 RAG 向量化时，context + question 一起 Embedding
 */
@Service
public class ChatLogParser {

    /** 合并消息的最大时间间隔：2 分钟 */
    private static final long MERGE_INTERVAL_MS = 2 * 60 * 1000;

    /** 事实提取保留 5 条 */
    private static final int CONTEXT_WINDOW_SIZE = 5;

    /** 场景超时：如果两条消息间隔超过 30 分钟，视为新场景，不带上下文 */
    private static final long SCENE_TIMEOUT_MS = 30 * 60 * 1000;

    /** 匹配图片/视频等媒体占位符 */
    private static final Pattern MEDIA_PATTERN = Pattern.compile(
            "^\\[(?:图片|视频|语音|文件)[:：].*]$");

    // ===== 内部方法 =====

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

    /** 过滤无效消息：空内容、媒体占位符、JSON卡片 */
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

    /** 合并同一人连续消息（2 分钟内拼接） */
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
     * 事实提取 2.0：阶梯式切片逻辑
     * 1. 第一刀：按 15 分钟停顿切成 Session
     * 2. 第二刀：Session 内部按 40 条（重叠 5 条）切成 Scene
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

    /** 第一刀：15 分钟绝对停顿切分 Session */
    private List<List<ParsedMessage>> splitIntoSessions(List<ParsedMessage> messages) {
        List<List<ParsedMessage>> sessions = new ArrayList<>();
        if (messages.isEmpty())
            return sessions;

        List<ParsedMessage> currentSession = new ArrayList<>();
        currentSession.add(messages.get(0));

        for (int i = 1; i < messages.size(); i++) {
            ParsedMessage prev = messages.get(i - 1);
            ParsedMessage curr = messages.get(i);

            // 如果相邻两条消息间隔超过 15 分钟，另起一个 Session
            if (curr.getTimestamp() - prev.getTimestamp() > 15 * 60 * 1000) {
                sessions.add(currentSession);
                currentSession = new ArrayList<>();
            }
            currentSession.add(curr);
        }
        sessions.add(currentSession);
        return sessions;
    }

    /** 第二刀：Session 内部按 40 条（重叠 5 条）切成 Scene */
    private List<List<ParsedMessage>> splitIntoScenes(List<ParsedMessage> session) {
        List<List<ParsedMessage>> scenes = new ArrayList<>();
        int size = session.size();
        int limit = 40; // 每块最多 40 条
        int overlap = 5; // 重叠 5 条

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
