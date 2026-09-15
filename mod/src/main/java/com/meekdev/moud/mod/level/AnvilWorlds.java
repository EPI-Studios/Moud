package com.meekdev.moud.mod.level;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.hollowcube.polar.PolarChunk;
import net.hollowcube.polar.PolarSection;
import net.hollowcube.polar.PolarWorld;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.jspecify.annotations.Nullable;

public final class AnvilWorlds {

    public record Dimension(String label, Path regions, int regionFiles) {}

    public record Plan(Dimension dimension, int centreX, int centreZ, int radius) {
        boolean wants(int chunkX, int chunkZ) {
            return radius <= 0 || Math.abs(chunkX - centreX) <= radius && Math.abs(chunkZ - centreZ) <= radius;
        }
    }

    public record Progress(int regionsDone, int regions, int chunks, int skipped) {}

    public static final class Cancelled extends IOException {
        public Cancelled() {
            super("the import was cancelled");
        }
    }

    private static final Pattern REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static final Set<String> FINISHED = Set.of("full", "spawn", "light", "initialize_light", "features");
    private static final int SECTOR = 4096;
    private static final int SECTION_BLOCKS = 4096;
    private static final int SECTION_BIOMES = 64;
    private static final int SEARCH_DEPTH = 5;
    private static final byte MIN_SECTION = -4;
    private static final byte MAX_SECTION = 19;

    private AnvilWorlds() {}

    public static List<Dimension> dimensions(Path world) {
        List<Dimension> found = new ArrayList<>();
        if (!Files.isDirectory(world)) return found;
        try (Stream<Path> walk = Files.walk(world, SEARCH_DEPTH)) {
            for (Path folder : walk.filter(Files::isDirectory).filter(p -> p.getFileName().toString().equals("region")).toList()) {
                int files = regionFiles(folder).size();
                if (files > 0) found.add(new Dimension(label(world, folder), folder, files));
            }
        } catch (IOException ignored) {
        }
        found.sort(Comparator.comparingInt((Dimension d) -> rank(d.label())).thenComparing(Dimension::label));
        return found;
    }

    public static int[] spawnChunk(Path world) {
        Path level = world.resolve("level.dat");
        if (!Files.isRegularFile(level)) return new int[] {0, 0};
        try {
            CompoundTag data = NbtIo.readCompressed(level, NbtAccounter.unlimitedHeap()).getCompoundOrEmpty("Data");
            CompoundTag spawn = data.getCompoundOrEmpty("spawn");
            int[] pos = spawn.getIntArray("pos").orElse(null);
            if (pos != null && pos.length >= 3) return new int[] {pos[0] >> 4, pos[2] >> 4};
            return new int[] {data.getIntOr("SpawnX", 0) >> 4, data.getIntOr("SpawnZ", 0) >> 4};
        } catch (IOException | RuntimeException e) {
            return new int[] {0, 0};
        }
    }

    public static int count(Plan plan) {
        int total = 0;
        for (Path file : regionFiles(plan.dimension().regions())) {
            int[] origin = origin(file);
            if (origin == null) continue;
            byte[] header = new byte[SECTOR];
            try (InputStream in = Files.newInputStream(file)) {
                if (in.readNBytes(header, 0, SECTOR) < SECTOR) continue;
            } catch (IOException e) {
                continue;
            }
            for (int index = 0; index < 1024; index++) {
                if (sectorOffset(header, index) == 0) continue;
                if (plan.wants(origin[0] * 32 + (index & 31), origin[1] * 32 + (index >> 5))) total++;
            }
        }
        return total;
    }

    public static PolarWorld convert(Plan plan, Consumer<Progress> progress, AtomicBoolean cancel) throws IOException {
        PolarWorld polar = new PolarWorld();
        polar.setSectionCount(MIN_SECTION, MAX_SECTION);
        List<Path> files = regionFiles(plan.dimension().regions());
        int current = SharedConstants.getCurrentVersion().dataVersion().version();
        int chunks = 0;
        int skipped = 0;
        for (int n = 0; n < files.size(); n++) {
            if (cancel.get()) throw new Cancelled();
            Path file = files.get(n);
            int[] origin = origin(file);
            if (origin == null || !regionTouches(plan, origin)) {
                progress.accept(new Progress(n + 1, files.size(), chunks, skipped));
                continue;
            }
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length < SECTOR * 2) continue;
            for (int index = 0; index < 1024; index++) {
                if (cancel.get()) throw new Cancelled();
                int chunkX = origin[0] * 32 + (index & 31);
                int chunkZ = origin[1] * 32 + (index >> 5);
                int offset = sectorOffset(bytes, index);
                if (offset == 0 || !plan.wants(chunkX, chunkZ)) continue;
                try {
                    CompoundTag tag = readChunk(file, bytes, offset, chunkX, chunkZ);
                    if (tag == null) continue;
                    int version = tag.getIntOr("DataVersion", 0);
                    if (version < current) tag = DataFixTypes.CHUNK.update(DataFixers.getDataFixer(), tag, version, current);
                    PolarChunk chunk = chunk(tag, chunkX, chunkZ);
                    if (chunk == null) {
                        skipped++;
                        continue;
                    }
                    polar.updateChunkAt(chunkX, chunkZ, chunk);
                    chunks++;
                } catch (IOException | RuntimeException e) {
                    skipped++;
                }
            }
            progress.accept(new Progress(n + 1, files.size(), chunks, skipped));
        }
        return polar;
    }

    private static @Nullable CompoundTag readChunk(Path file, byte[] bytes, int sector, int chunkX, int chunkZ) throws IOException {
        int at = sector * SECTOR;
        if (at + 5 > bytes.length) return null;
        int length = (bytes[at] & 0xFF) << 24 | (bytes[at + 1] & 0xFF) << 16 | (bytes[at + 2] & 0xFF) << 8 | bytes[at + 3] & 0xFF;
        int kind = bytes[at + 4] & 0xFF;
        InputStream raw;
        if ((kind & 0x80) != 0) {
            Path external = file.resolveSibling("c." + chunkX + "." + chunkZ + ".mcc");
            if (!Files.isRegularFile(external)) return null;
            raw = Files.newInputStream(external);
            kind &= 0x7F;
        } else {
            if (length <= 1 || at + 4 + length > bytes.length) return null;
            raw = new ByteArrayInputStream(bytes, at + 5, length - 1);
        }
        RegionFileVersion compression = RegionFileVersion.fromId(kind);
        if (compression == null) return null;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(compression.wrap(raw)))) {
            return NbtIo.read(in, NbtAccounter.unlimitedHeap());
        }
    }

    static @Nullable PolarChunk chunk(CompoundTag tag, int chunkX, int chunkZ) {
        String status = tag.getStringOr("Status", "full");
        if (!FINISHED.contains(status.substring(status.indexOf(':') + 1))) return null;
        PolarSection[] sections = new PolarSection[MAX_SECTION - MIN_SECTION + 1];
        for (int n = 0; n < sections.length; n++) sections[n] = new PolarSection();
        boolean any = false;
        ListTag list = tag.getListOrEmpty("sections");
        for (int n = 0; n < list.size(); n++) {
            CompoundTag section = list.getCompoundOrEmpty(n);
            int y = section.getByteOr("Y", Byte.MIN_VALUE);
            if (y < MIN_SECTION || y > MAX_SECTION) continue;
            PolarSection converted = section(section);
            if (converted == null) continue;
            sections[y - MIN_SECTION] = converted;
            any = true;
        }
        if (!any) return null;
        return new PolarChunk(chunkX, chunkZ, sections, List.of(), new int[PolarChunk.MAX_HEIGHTMAPS][], new byte[0]);
    }

    private static @Nullable PolarSection section(CompoundTag section) {
        CompoundTag states = section.getCompoundOrEmpty("block_states");
        ListTag palette = states.getListOrEmpty("palette");
        if (palette.isEmpty()) return null;
        String[] blocks = new String[palette.size()];
        for (int n = 0; n < blocks.length; n++) blocks[n] = state(palette.getCompoundOrEmpty(n));
        if (blocks.length == 1 && isAir(blocks[0])) return null;
        int[] blockData = null;
        if (blocks.length > 1) {
            long[] packed = states.getLongArray("data").orElse(null);
            if (packed == null) return null;
            blockData = unpack(packed, Math.max(4, bits(blocks.length)), SECTION_BLOCKS);
        }
        CompoundTag biomes = section.getCompoundOrEmpty("biomes");
        ListTag biomeList = biomes.getListOrEmpty("palette");
        String[] biomePalette = new String[Math.max(1, biomeList.size())];
        biomePalette[0] = "minecraft:plains";
        for (int n = 0; n < biomeList.size(); n++) biomePalette[n] = biomeList.getStringOr(n, "minecraft:plains");
        int[] biomeData = null;
        if (biomePalette.length > 1) {
            long[] packed = biomes.getLongArray("data").orElse(null);
            if (packed == null) biomePalette = new String[] {biomePalette[0]};
            else biomeData = unpack(packed, bits(biomePalette.length), SECTION_BIOMES);
        }
        return new PolarSection(blocks, blockData, biomePalette, biomeData,
                PolarSection.LightContent.MISSING, null, PolarSection.LightContent.MISSING, null);
    }

    static String state(CompoundTag entry) {
        String name = entry.getStringOr("Name", "minecraft:air");
        CompoundTag properties = entry.getCompoundOrEmpty("Properties");
        if (properties.isEmpty()) return name;
        StringBuilder out = new StringBuilder(name).append('[');
        boolean first = true;
        for (String key : properties.keySet().stream().sorted().toList()) {
            Tag value = properties.get(key);
            if (!first) out.append(',');
            out.append(key).append('=').append(value == null ? "" : value.asString().orElse(""));
            first = false;
        }
        return out.append(']').toString();
    }

    static int[] unpack(long[] packed, int bits, int count) {
        int[] out = new int[count];
        int perLong = 64 / bits;
        long mask = (1L << bits) - 1;
        for (int n = 0; n < count; n++) {
            int word = n / perLong;
            if (word >= packed.length) break;
            out[n] = (int) (packed[word] >>> (n % perLong * bits) & mask);
        }
        return out;
    }

    private static int bits(int size) {
        return size <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(size - 1);
    }

    private static boolean isAir(String state) {
        return state.equals("minecraft:air") || state.equals("minecraft:cave_air") || state.equals("minecraft:void_air");
    }

    private static int sectorOffset(byte[] header, int index) {
        int at = index * 4;
        return (header[at] & 0xFF) << 16 | (header[at + 1] & 0xFF) << 8 | header[at + 2] & 0xFF;
    }

    private static boolean regionTouches(Plan plan, int[] origin) {
        if (plan.radius() <= 0) return true;
        int minX = origin[0] * 32;
        int minZ = origin[1] * 32;
        return minX + 31 >= plan.centreX() - plan.radius() && minX <= plan.centreX() + plan.radius()
                && minZ + 31 >= plan.centreZ() - plan.radius() && minZ <= plan.centreZ() + plan.radius();
    }

    private static int @Nullable [] origin(Path file) {
        Matcher match = REGION.matcher(file.getFileName().toString());
        return match.matches() ? new int[] {Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2))} : null;
    }

    private static List<Path> regionFiles(Path folder) {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(p -> REGION.matcher(p.getFileName().toString()).matches()).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String label(Path world, Path regions) {
        String relative = world.relativize(regions).toString().replace('\\', '/');
        return switch (relative) {
            case "region", "dimensions/minecraft/overworld/region" -> "Overworld";
            case "DIM-1/region", "dimensions/minecraft/the_nether/region" -> "Nether";
            case "DIM1/region", "dimensions/minecraft/the_end/region" -> "The End";
            default -> relative.substring(0, Math.max(0, relative.length() - "/region".length()));
        };
    }

    private static int rank(String label) {
        return switch (label) {
            case "Overworld" -> 0;
            case "Nether" -> 1;
            case "The End" -> 2;
            default -> 3;
        };
    }
}
