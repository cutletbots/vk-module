package ru.blc.cutlet.vk.command;

import ru.blc.cutlet.api.bean.ChatUser;
import ru.blc.cutlet.api.bot.Bot;
import ru.blc.cutlet.api.command.sender.CommandSender;
import ru.blc.cutlet.api.command.sender.TargetSearchResult;
import ru.blc.cutlet.vk.VkModule;
import ru.blc.cutlet.vk.method.messages.Send;
import ru.blc.cutlet.vk.objects.main.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public interface VkCommandSender extends CommandSender {

    Pattern TAG_PATTERN = Pattern.compile("\\[id(\\d+)\\|[^]]+]");

    int getPeerId();

    int getFromId();

    Message getMessage();

    Send.SendParamsSet getAnswerSet();

    Send.SendParamsSet getAnswerSet(String text);

    Bot getBot();

    @Override
    default ChatUser getUserSender() {
        return new ChatUser(VkModule.VK_MESSENGER, getFromId());
    }

    @Override
    default List<TargetSearchResult> extractTargets(TargetSearchResult.FindCase... filter) {
        List<TargetSearchResult> targets = new ArrayList<>();
        try {
            targets.addAll(extractFromForward());
            targets.addAll(extractTagged());
        } catch (Throwable ignored) {
        }
        Set<TargetSearchResult.FindCase> filters = Set.of(filter);
        if (filters.isEmpty()) return targets;
        return targets.stream()
                .filter(t -> filters.contains(t.findCase()))
                .collect(Collectors.toList());
    }

    default List<TargetSearchResult> extractFromForward() {
        List<TargetSearchResult> targets = new ArrayList<>();
        if (getMessage().getReplyMessage() != null) {
            ChatUser target = new ChatUser(VkModule.VK_MESSENGER, getMessage().getReplyMessage().getFromId());
            targets.add(new TargetSearchResult().target(target).findCase(TargetSearchResult.FindCase.FORWARD));
        }
        if (getMessage().getFwdMessages() != null) {
            for (Message fwdMessage : getMessage().getFwdMessages()) {
                ChatUser target = new ChatUser(VkModule.VK_MESSENGER, fwdMessage.getFromId());
                targets.add(new TargetSearchResult().target(target).findCase(TargetSearchResult.FindCase.FORWARD));
            }
        }
        return targets;
    }

    default List<TargetSearchResult> extractTagged() {
        List<TargetSearchResult> targets = new ArrayList<>();
        String text = Optional.ofNullable(getMessage()).map(Message::getText).orElse("");
        Matcher m = TAG_PATTERN.matcher(text);
        while (m.find()) {
            long id = Long.parseLong(m.group(1));
            targets.add(new TargetSearchResult()
                    .target(new ChatUser(VkModule.VK_MESSENGER, id))
                    .findCase(TargetSearchResult.FindCase.MENTION)
                    .targetText(text.substring(m.start(), m.end()))
                    .startIndex(m.start())
                    .endIndex(m.end()));
        }
        return targets;
    }
}
