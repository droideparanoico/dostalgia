package org.dostalgia;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Contract for game metadata persistence.
 * Decouples consumers (IgdbService, GameController) from the concrete storage implementation.
 */
public interface GameStore {
    Game load(String id) throws IOException;
    void save(Game game) throws IOException;
    List<Game> list() throws IOException;
    void delete(String id) throws IOException;
    Path gameDir(String id);
    Path gameJsonPath(String id);
}
