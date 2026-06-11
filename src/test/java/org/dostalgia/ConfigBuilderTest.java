package org.dostalgia;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigBuilderTest {

    private final ConfigBuilder builder = new ConfigBuilder();

    @Test
    void buildDosboxConf_noCdImages() {
        String conf = builder.buildDosboxConf("FALLOUT.EXE", List.of());
        assertTrue(conf.contains("memsize=64"), "Should include memsize");
        assertTrue(conf.contains("mount c ."), "Should mount C drive");
        assertTrue(conf.contains("FALLOUT.EXE"), "Should reference executable");
        assertFalse(conf.contains("imgmount"), "No CD images should produce no imgmount");
    }

    @Test
    void buildDosboxConf_withCdImages() {
        String conf = builder.buildDosboxConf("DOTT.EXE", List.of("game.cue", "game.bin"));
        assertTrue(conf.contains("imgmount D \"game.bin\" -t cdrom -fs iso"), "Should mount .bin with iso flag");
        assertFalse(conf.contains("game.cue"), "Should skip .cue when .bin exists");
    }

    @Test
    void buildDosboxConf_subdirectory() {
        String conf = builder.buildDosboxConf("FALLOUT/FALLOUT.EXE", List.of());
        assertTrue(conf.contains("cd FALLOUT"), "Should cd into subdirectory");
        assertTrue(conf.contains("path=c:\\"), "Should set path");
    }

    @Test
    void buildCdOnlyDosboxConf() {
        String conf = builder.buildCdOnlyDosboxConf(List.of("game.iso"));
        assertTrue(conf.contains("runs from the CD-ROM"), "CD-only message");
        assertTrue(conf.contains("imgmount D"), "Should mount CD");
    }

    @Test
    void buildJsdosJson() {
        byte[] json = builder.buildJsdosJson("GAME.EXE", List.of());
        String s = new String(json);
        assertTrue(s.contains("\"script\""), "Should have script key");
        assertTrue(s.contains("\"autolock\""), "Should have autolock");
        assertTrue(s.contains("GAME.EXE"), "Should contain executable name");
    }

    @Test
    void splitExePath_rootLevel() {
        var parts = ConfigBuilder.splitExePath("GAME.EXE");
        assertNull(parts.dir());
        assertEquals("GAME.EXE", parts.exe());
    }

    @Test
    void splitExePath_subdirectory() {
        var parts = ConfigBuilder.splitExePath("FALLOUT1/FALLOUT.EXE");
        assertEquals("FALLOUT1", parts.dir());
        assertEquals("FALLOUT.EXE", parts.exe());
    }

    @Test
    void splitExePath_nullOrEmpty() {
        assertNull(ConfigBuilder.splitExePath(null).dir());
        assertEquals("", ConfigBuilder.splitExePath("").exe());
    }

    @Test
    void escapeJson() {
        assertEquals("hello", ConfigBuilder.escapeJson("hello"));
        assertEquals("\\\\n", ConfigBuilder.escapeJson("\\n"));
        assertEquals("he said \\\"hi\\\"", ConfigBuilder.escapeJson("he said \"hi\""));
    }
}
