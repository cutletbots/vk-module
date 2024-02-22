package ru.blc.cutlet.vk.longpoll;

import org.jetbrains.annotations.NotNull;
import ru.blc.cutlet.vk.VkBot;

import java.util.HashMap;
import java.util.Map;

public class LongPollManager {

    private final Map<VkBot, LongPollConnectionV2> connections = new HashMap<>();
    private final LongPollConnectionsThread connectionsThread;
    private final LongPollUpdatesThread updatesThread;

    public LongPollManager() {
        updatesThread = new LongPollUpdatesThread();
        connectionsThread = new LongPollConnectionsThread(updatesThread::addUpdates);
        updatesThread.start();
        connectionsThread.start();
    }

    public void stop() {
        connections.clear();
        connectionsThread.finish();
        updatesThread.finish();
    }


    /**
     * Возвращает текущее LongPoll подключение для указанного бота
     * или создает новое, если ни одного нет
     *
     * @param bot бот
     * @return соединение
     */
    public LongPollConnectionV2 getConnection(@NotNull VkBot bot) {
        LongPollConnectionV2 connection = connections.get(bot);
        if (connection == null) {
            connection = new LongPollConnectionV2(bot);
            connections.put(bot, connection);
            connectionsThread.addConnection(connection);
        }
        return connection;
    }

    /**
     * Прекращает текущее LongPoll подключение для этого бота
     *
     * @param bot бот
     * @return true, если подключение существовало, false если бот не был подключен к LongPoll
     */
    public boolean disconnect(@NotNull VkBot bot) {
        LongPollConnectionV2 connection = connections.remove(bot);
        if (connection == null) {
            return false;
        }
        connection.stop();
        return true;
    }
}
