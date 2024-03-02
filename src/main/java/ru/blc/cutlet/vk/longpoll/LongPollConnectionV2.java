package ru.blc.cutlet.vk.longpoll;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import ru.blc.cutlet.api.Cutlet;
import ru.blc.cutlet.vk.VkBot;
import ru.blc.cutlet.vk.VkModule;
import ru.blc.objconfig.ConfigurationSection;
import ru.blc.objconfig.json.JsonConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Getter
@Accessors(fluent = true)
public class LongPollConnectionV2 {
    private static final VkModule VK_MODULE = Cutlet.instance().getModule(VkModule.class);

    private final VkBot bot;
    private boolean running;
    private boolean init;
    private boolean lastUpdateFailed = false;

    private int reinitAttempts = 0;
    private long lastReinit;

    private final ReentrantLock connectionLock = new ReentrantLock();

    private String key, server;
    private String ts;

    protected LongPollConnectionV2(VkBot bot) {
        this.bot = bot;
        this.running = true;
        lastReinit = System.currentTimeMillis();
    }

    public void stop() {
        this.running = false;
    }

    /**
     * Blocking method
     */
    protected void initConnection() {
        connectionLock.lock();
        init = true;
        try {
            //задержка чтобы не ддосить вк
            if (lastUpdateFailed) {
                long delay;
                if (reinitAttempts <= 3) {
                    delay = 3000;
                } else if (reinitAttempts <= 6) {
                    delay = 5000;
                } else if (reinitAttempts <= 10) {
                    delay = 10000;
                } else {
                    delay = 15000;
                }

                if (delay > System.currentTimeMillis() - lastReinit) {
                    return;
                }
                reinitAttempts++;
                lastReinit = System.currentTimeMillis();
            }

            JsonConfiguration json = JsonConfiguration.loadConfiguration(VkModule.METHODS
                    .groups
                    .getLongPollServer
                    .getNewParamsSet()
                    .setGroupId(bot.getGroupId())
                    .setToken(bot.getDefaultToken())
                    .callAwait());
            if (json.hasValue("error")) {
                lastUpdateFailed = true;
                bot.getLogger().error("Error {} while creating long poll connection for bot {}. Message: {}",
                        json.get("error.error_code"), bot.getName(), json.get("error.error_msg"));
                return;
            }

            key = json.getString("response.key");
            server = json.getString("response.server");
            ts = json.getString("response.ts");
            VK_MODULE.getLogger().info("Long Poll connection for bot {} successfully started with {} attempts",
                    bot.getName(), reinitAttempts);
            lastUpdateFailed = false;
            reinitAttempts = 0;

        } catch (Throwable t) {
            bot.getLogger().error("Error while creating long poll connection for bot " + bot.getName(), t);
            lastUpdateFailed = true;
            return;
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Blocking method
     */
    protected List<LongPollUpdate> getUpdates() {
        if (lastUpdateFailed) {
            return null;
        }
        connectionLock.lock();
        String adr = server;
        List<LongPollUpdate> result = new ArrayList<>();
        RequestConfig globalConfig = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.DEFAULT)
                .build();
        try (CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultRequestConfig(globalConfig)
                .build()) {
            RequestConfig localConfig = RequestConfig.copy(globalConfig)
                    .setCookieSpec(CookieSpecs.STANDARD)
                    .build();
            HttpPost httppost = new HttpPost(adr);
            httppost.setConfig(localConfig);
            // Request parameters and other properties.
            List<NameValuePair> pars = new ArrayList<>();
            pars.add(new BasicNameValuePair("act", "a_check"));
            pars.add(new BasicNameValuePair("key", key));
            pars.add(new BasicNameValuePair("ts", String.valueOf(ts)));
            pars.add(new BasicNameValuePair("wait", "25"));
            httppost.setEntity(new UrlEncodedFormEntity(pars, "UTF-8"));


            // Execute and get the response.
            HttpResponse response = httpclient.execute(httppost);
            HttpEntity entity = response.getEntity();

            byte[] data;
            if (entity == null) {
                data = new byte[0];
            } else {
                data = EntityUtils.toByteArray(entity);
            }
            int bytesl = data.length;
            String json;
            if (bytesl > 0) {
                json = new String(data, StandardCharsets.UTF_8);
            } else {
                VK_MODULE.getLogger().debug("Empty data at updates for bot {}", bot.getName());
                lastUpdateFailed = true;
                return result;
            }
            JsonConfiguration updates = JsonConfiguration.loadConfiguration(json);
            if (updates.hasValue("failed")) {
                VK_MODULE.getLogger().debug("Fail {} on update. Bot {}", updates.getInt("failed"), bot.getName());
                if (updates.getInt("failed") == 1) {
                    ts = updates.getString("ts");
                    return result;
                }
                lastUpdateFailed = true;
                return result;
            }
            VK_MODULE.getLogger().debug("Normal update got. Bot {}", bot.getName());

            ts = updates.getString("ts");
            for (ConfigurationSection update : updates.getConfigurationSectionList("updates")) {
                update.set("secret", bot.getSecret());
                result.add(new LongPollUpdate(bot, update));
            }
            VK_MODULE.getLogger().debug("Parsed {} updates for bot {}", result.size(), bot.getName());
            return result;
        } catch (IOException ex) {
            lastUpdateFailed = true;
            VK_MODULE.getLogger().error("can't get updates for bot {}", bot.getName(), ex);
        } finally {
            connectionLock.unlock();
        }
        return result;
    }
}
