package com.meekdev.moud.core.nav;

final class TileGrid {

    static final float CELL = 0.25f;
    static final float CELL_HEIGHT = 0.1f;
    static final int CELLS_PER_BLOCK = Math.round(1 / CELL);
    static final int CELLS_PER_LAYER = Math.round(1 / CELL_HEIGHT);
    static final int TILE_CELLS = 128;
    static final float TILE_SIZE = TILE_CELLS * CELL;
    static final int TILE_BLOCKS = Math.round(TILE_SIZE);

    private TileGrid() {}

    static int tileIndex(double x) {
        return (int) Math.floor(x / TILE_SIZE);
    }
}
