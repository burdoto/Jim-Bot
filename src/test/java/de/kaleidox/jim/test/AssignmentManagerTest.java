package de.kaleidox.jim.test;

import java.text.ParseException;

import org.junit.Test;

import static de.kaleidox.jim.AssignmentManager.extractTime;
import static org.junit.Assert.assertEquals;

public class AssignmentManagerTest {
    @Test
    public void testTimeExtraction() throws ParseException {
        assertEquals(330, extractTime("5m30s"));

        assertEquals(600, extractTime("10m"));

        assertEquals(60, extractTime("1"));

        assertEquals(285225, extractTime("3d7h13m45s"));
    }
}
