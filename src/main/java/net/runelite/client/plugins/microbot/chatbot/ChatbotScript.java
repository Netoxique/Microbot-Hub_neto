// build.gradle (or your deps)
// implementation("com.openai:openai-java:3.6.1")  // check latest in docs

// ChatAutoReplyPlugin.java
package com.yourorg.microbot.plugins.chatauto;

import com.google.inject.Provides;
import com.openai.OpenAI;
import com.openai.core.RetryingExecutor;
import com.openai.models.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;

import javax.inject.Inject;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ChatAutoReplyPlugin extends Plugin {

    @Inject private Client client;
    @Inject private ChatAutoReplyConfig config;

    private OpenAI openAI;

    private static final int MAX_HISTORY = 8; // per player
    private static final long COOLDOWN_MS = 30_000;
    private static final long GLOBAL_MIN_GAP_MS = 10_000;

    private final Map<String, Deque<ChatLine>> history = new ConcurrentHashMap<>();
    private final Map<String, Long> lastReplyPerUser = new ConcurrentHashMap<>();
    private volatile long lastGlobalReply = 0L;

    private record ChatLine(String speaker, String text, ChatMessageType type, Instant at) {}

    @Override
    protected void startUp() {
        // Create OpenAI client (read key from env or config)
        openAI = OpenAI.builder()
                .apiKey(config.apiKey().trim())
                .build();
        log.info("ChatAutoReplyPlugin started");
    }

    @Override
    protected void shutDown() {
        log.info("ChatAutoReplyPlugin stopped");
    }

    @Subscribe
    public void onChatMessage(ChatMessage ev) {
        ChatMessageType t = ev.getType();

        // Only consider player chat varieties you want:
        if (!isPlayerChat(t)) return;

        String self = safe(selfName());
        if (self.isEmpty()) return;

        String speaker = safe(ev.getName());         // other player
        String text    = safe(ev.getMessage());      // stripped of tags by RuneLite already
        if (speaker.isEmpty() || speaker.equalsIgnoreCase(self)) return;

        // Track conversation if message mentions you or is a continuation
        boolean mentionsMe = text.toLowerCase().contains(self.toLowerCase());
        boolean inThread = isRecent(history.get(speaker));

        if (!mentionsMe && !inThread) {
            // Optionally, also track everyone but only reply if mentioned
            if (config.trackAll()) append(speaker, text, t);
            return;
        }

        append(speaker, text, t);

        if (!shouldReplyNow(speaker)) return;
        if (isBlacklisted(text, config.blacklist())) return;

        // Build prompt & messages
        List<ChatCompletionMessageParam> msgs = buildMessages(self, speaker, history.get(speaker), config);

        try {
            // Call OpenAI
            var resp = openAI.chat().completions().create(ChatCompletionCreateParams.builder()
                    .model(config.modelId())  // e.g., "gpt-4.1-mini" or your choice
                    .messages(msgs)
                    .temperature(config.temperature())
                    .maxTokens(config.maxReplyTokens())
                    .build());

            String reply = resp.getChoices().get(0).getMessage().getContent();
            if (reply == null || reply.isBlank()) return;

            reply = sanitizeReply(reply, config.maxLineLength());
            sendInGameChat(reply, t); // implement this for your client (see note below)

            lastReplyPerUser.put(speaker, System.currentTimeMillis());
            lastGlobalReply = System.currentTimeMillis();
        } catch (Exception ex) {
            log.warn("OpenAI chat error", ex);
        }
    }

    private boolean isPlayerChat(ChatMessageType t) {
        return t == ChatMessageType.PUBLICCHAT
                || t == ChatMessageType.PRIVATECHAT
                || t == ChatMessageType.FRIENDSCHAT
                || t == ChatMessageType.CLAN_CHAT
                || t == ChatMessageType.CLAN_GUEST_CHAT;
    }

    private String selfName() {
        return client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "";
    }

    private void append(String speaker, String text, ChatMessageType t) {
        history.computeIfAbsent(speaker, k -> new ArrayDeque<>());
        Deque<ChatLine> q = history.get(speaker);
        q.addLast(new ChatLine(speaker, text, t, Instant.now()));
        while (q.size() > MAX_HISTORY) q.removeFirst();
    }

    private boolean isRecent(Deque<ChatLine> q) {
        if (q == null || q.isEmpty()) return false;
        return Instant.now().minusSeconds(60).isBefore(q.getLast().at());
    }

    private boolean shouldReplyNow(String speaker) {
        long now = System.currentTimeMillis();
        long lastForUser = lastReplyPerUser.getOrDefault(speaker, 0L);
        return now - lastForUser >= COOLDOWN_MS && now - lastGlobalReply >= GLOBAL_MIN_GAP_MS;
    }

    private boolean isBlacklisted(String text, Set<String> blacklist) {
        if (blacklist == null) return false;
        String lc = text.toLowerCase();
        for (String bad : blacklist) if (lc.contains(bad.toLowerCase())) return true;
        return false;
    }

    private List<ChatCompletionMessageParam> buildMessages(
            String self, String other, Deque<ChatLine> log, ChatAutoReplyConfig cfg) {

        String policy = """
                You are an in-game chat assistant for an OSRS player.
                Keep replies short (<= 120 chars), friendly, and *never* discuss RWT, selling gold, account sharing, bots, scripts, or rule-breaking.
                Avoid giving gameplay advantages that look like automation. Decline anything suspicious.
                """;

        List<ChatCompletionMessageParam> msgs = new ArrayList<>();
        msgs.add(ChatCompletionMessageParam.system(system -> system.content(cfg.basePrompt() + "\n\n" + policy)));
        msgs.add(ChatCompletionMessageParam.user(user -> user.content(
                "My in-game name: " + self + "\n" +
                        "Talking to: " + other + "\n" +
                        "Channel: " + (log != null && !log.isEmpty() ? log.getLast().type() : "unknown") + "\n" +
                        "Recent lines (oldest → newest):\n" + renderTranscript(log)
        )));
        return msgs;
    }

    private String renderTranscript(Deque<ChatLine> log) {
        if (log == null) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (ChatLine c : log) {
            sb.append("[").append(c.type()).append("] ")
                    .append(c.speaker()).append(": ").append(c.text()).append("\n");
        }
        return sb.toString();
    }

    private String sanitizeReply(String s, int max) {
        s = s.replaceAll("\\r?\\n", " ").trim();
        if (s.length() > max) s = s.substring(0, max - 1) + "…";
        // OSRS chat disallows some characters—keep it plain ASCII if needed:
        return s;
    }

    private String safe(String s) { return s == null ? "" : s; }

    private void sendInGameChat(String msg, ChatMessageType replyChannel) {
        // TODO: implement. Depending on your Microbot build, you might have a helper.
        // Examples in some forks use utilities like Rs2Game/Rs2Utils or direct key events.
        // Keep it to the same channel as the inbound message if possible.
    }

    @Provides
    ChatAutoReplyConfig provideConfig(ConfigManager cm) {
        return cm.getConfig(ChatAutoReplyConfig.class);
    }
}
