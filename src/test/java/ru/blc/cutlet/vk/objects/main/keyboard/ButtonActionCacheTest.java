package ru.blc.cutlet.vk.objects.main.keyboard;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.blc.objconfig.json.JsonConfiguration;

public class ButtonActionCacheTest {
    @Test
    public void isPermissionAllows() {
        JsonConfiguration c = new JsonConfiguration();
        for (int i = 0; i < 5000; i++) {
            new ButtonAction("a", null).write(c);
        }
        Assertions.assertNotNull(ButtonAction.getAction(4500), "Wrong cache system");
    }
}