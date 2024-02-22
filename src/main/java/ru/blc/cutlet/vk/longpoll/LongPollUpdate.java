package ru.blc.cutlet.vk.longpoll;

import ru.blc.cutlet.vk.VkBot;
import ru.blc.objconfig.ConfigurationSection;


public record LongPollUpdate(VkBot bot, ConfigurationSection update) {

}
