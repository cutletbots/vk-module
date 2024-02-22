package ru.blc.cutlet.vk.longpoll;

import ru.blc.cutlet.api.Cutlet;
import ru.blc.cutlet.vk.VkBot;
import ru.blc.cutlet.vk.VkModule;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class LongPollConnectionsThread extends Thread {
    private static final VkModule VK_MODULE = Cutlet.instance().getModule(VkModule.class);

    private final LinkedBlockingQueue<LongPollConnectionV2> connectionQueue = new LinkedBlockingQueue<>();
    private final Consumer<List<LongPollUpdate>> updatesConsumer;
    private boolean running;

    public LongPollConnectionsThread(Consumer<List<LongPollUpdate>> updatesConsumer) {
        this.running = true;
        this.updatesConsumer = updatesConsumer;
        this.setName("VK LPC Thread");
        this.setDaemon(true);
    }

    public void finish() {
        running = false;
    }

    protected void addConnection(LongPollConnectionV2 connection) {
        connectionQueue.add(connection);
    }

    @Override
    public void run() {
        while (running) {
            LongPollConnectionV2 conn;
            try {
                //получаем первого в очереди, ожидание не больше 50 милисек
                conn = connectionQueue.poll(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                //нам пиздец...
                VK_MODULE.getLogger().error("Long Poll Connections Thread interrupted!", e);
                break;
            }
            //если никого не получили - заново
            if (conn == null) {
                continue;
            }

            //Остановлено
            if (!conn.running()) {
                //не добавляем его в очередь и приступаем к следующему
                continue;
            }

            VkBot bot = conn.bot();
            VK_MODULE.getLogger().debug("Getting update, Bot {}", bot.getName());

            //если коннект занят - кидаем в конец очереди
            if (conn.connectionLock().isLocked()) {
                connectionQueue.add(conn);
                continue;
            }

            //если коннект не готов, или обосрался ранее
            if (conn.lastUpdateFailed() || !conn.init()) {
                //инициализируем заново, по завершению добавляем в нашу очередь
                conn.initConnection().whenComplete((s, throwable) -> {
                    connectionQueue.add(conn);
                });
                continue;
            }

            //получаем список апдейтов
            List<LongPollUpdate> updates = conn.getUpdates();
            VK_MODULE.getLogger().debug("Got {} updates for bot {}", updates.size(), bot.getName());
            //передаем их получателю
            updatesConsumer.accept(updates);
            //коннект в конец очереди
            connectionQueue.add(conn);
        }
        VK_MODULE.getLogger().info("Long Poll Connections Thread stopped");
    }
}