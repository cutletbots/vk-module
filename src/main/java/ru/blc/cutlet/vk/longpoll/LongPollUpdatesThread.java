package ru.blc.cutlet.vk.longpoll;

import ru.blc.cutlet.api.Cutlet;
import ru.blc.cutlet.vk.VkModule;
import ru.blc.objconfig.json.JsonConfiguration;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LongPollUpdatesThread extends Thread {

    private static final VkModule VK_MODULE = Cutlet.instance().getModule(VkModule.class);
    private final LinkedBlockingQueue<LongPollUpdate> updatesQueue = new LinkedBlockingQueue<>();
    private boolean running;

    public LongPollUpdatesThread() {
        running = true;
        this.setName("VK LPU Thread");
        this.setDaemon(true);
    }

    public void finish() {
        running = false;
    }

    public void addUpdates(Collection<LongPollUpdate> updates) {
        updatesQueue.addAll(updates);
    }

    @Override
    public void run() {
        while (running) {
            LongPollUpdate update;
            try {
                //получаем первого в очереди, ожидание не больше 10 милисек
                update = updatesQueue.poll(10, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                //нам пиздец...
                VK_MODULE.getLogger().error("Long Poll Updates Thread interrupted!", e);
                break;
            }
            //если никого не получили - заново
            if (update == null) {
                continue;
            }
            try {
                Optional.ofNullable(update.bot().getJsonHandler())
                        .orElse(VK_MODULE.getJsonHandler())
                        .handleJson(update.update(), update.bot());
            } catch (Exception e) {
                VK_MODULE.getLogger().error("Failed to parse update {} for bot {}",
                        JsonConfiguration.createFromSection(update.update()).saveToString(), update.bot().getName());
            }
        }
        VK_MODULE.getLogger().info("Long Poll Updates Thread stopped");
    }
}
