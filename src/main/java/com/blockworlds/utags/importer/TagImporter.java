package com.blockworlds.utags.importer;

import com.blockworlds.utags.Tag;
import com.blockworlds.utags.TagType;
import com.blockworlds.utags.tags.TagRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * {@code /tag admin import <file>}: creates or updates tags from {@code plugins/UTags/import/<file>.yml}, through
 * the tag registry. Running a file twice changes nothing the second time (tags that already match are skipped).
 * <pre>
 * tags:
 *   - name: islander-2012
 *     display: "&amp;6[Islander since 2012]"
 *     type: suffix          # prefix, suffix or both (default both)
 *     public: false         # default false
 *     colorable: false      # default true
 *     weight: 2012          # default 0
 *     material: NAME_TAG    # default NAME_TAG
 * </pre>
 * Call {@link #run} on the main thread (it builds item stacks); it completes there too.
 */
public final class TagImporter {

    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    /** One tag as the file defines it. */
    public record Spec(String name, String display, TagType type, boolean isPublic, boolean colorable, int weight, String material) {
    }

    /** What an import did. {@code errors} are entries that were left out. */
    public record Report(int created, int updated, int skipped, List<String> errors) {
    }

    /** The parsed file: valid specs and a message for each entry that was left out. */
    public record Parsed(List<Spec> specs, List<String> errors) {
    }

    private final TagRegistry tags;

    public TagImporter(TagRegistry tags) {
        this.tags = tags;
    }

    /** The import file for a name given in the command, or null when the name is not a plain file name. */
    public static File resolve(File dataFolder, String name) {
        String base = name.endsWith(".yml") ? name.substring(0, name.length() - 4) : name;
        if (!FILE_NAME.matcher(base).matches() || base.contains("..")) return null;
        return new File(new File(dataFolder, "import"), base + ".yml");
    }

    public static Parsed parse(YamlConfiguration yaml) {
        List<Spec> specs = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Map<?, ?>> entries = yaml.getMapList("tags");
        if (entries.isEmpty()) errors.add("the file has no 'tags' list");
        int index = 0;
        for (Map<?, ?> entry : entries) {
            index++;
            Object rawName = entry.get("name");
            String name = rawName == null ? null : String.valueOf(rawName).trim();
            String where = "entry " + index + (name == null ? "" : " (" + name + ")");
            if (name == null || name.isEmpty() || name.length() > 255) {
                errors.add(where + ": missing or invalid name");
                continue;
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                errors.add(where + ": the name appears twice in the file");
                continue;
            }
            Object display = entry.get("display");
            if (display == null || String.valueOf(display).isEmpty()) {
                errors.add(where + ": missing display");
                continue;
            }
            TagType type;
            try {
                type = TagType.valueOf(String.valueOf(entry.containsKey("type") ? entry.get("type") : "both").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                errors.add(where + ": type must be prefix, suffix or both");
                continue;
            }
            String material = String.valueOf(entry.containsKey("material") ? entry.get("material") : "NAME_TAG").trim().toUpperCase(Locale.ROOT);
            int weight;
            try {
                weight = entry.containsKey("weight") ? Integer.parseInt(String.valueOf(entry.get("weight"))) : 0;
            } catch (NumberFormatException e) {
                errors.add(where + ": weight must be a whole number");
                continue;
            }
            specs.add(new Spec(name, String.valueOf(display), type, flag(entry.get("public"), false),
                    flag(entry.get("colorable"), true), weight, material));
        }
        return new Parsed(specs, errors);
    }

    private static boolean flag(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    /** Applies the file one tag at a time (each write reloads the registry), then reports. */
    public CompletableFuture<Report> run(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(new Report(0, 0, 0, List.of("could not read " + file.getName() + ": " + e.getMessage())));
        }
        Parsed parsed = parse(yaml);
        int[] counts = new int[3]; // created, updated, skipped
        List<String> errors = new ArrayList<>(parsed.errors());
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Spec spec : parsed.specs()) {
            chain = chain.thenCompose(v -> apply(spec, counts, errors));
        }
        return chain.thenApply(v -> new Report(counts[0], counts[1], counts[2], errors));
    }

    private CompletableFuture<Void> apply(Spec spec, int[] counts, List<String> errors) {
        Material material = Material.matchMaterial(spec.material());
        if (material == null || material.isAir() || !material.isItem()) {
            errors.add(spec.name() + ": unknown material " + spec.material());
            return CompletableFuture.completedFuture(null);
        }
        Tag existing = tags.byName(spec.name());
        if (existing != null && matches(existing, spec, material)) {
            counts[2]++;
            return CompletableFuture.completedFuture(null);
        }
        // an update keeps what the file does not define: the stored colour flag and, for a custom tag, owner and slot
        Tag tag = new Tag(existing == null ? spec.name() : existing.getName(), spec.display(), spec.type(), spec.isPublic(),
                existing != null && existing.isColor(), spec.colorable(), new ItemStack(material), spec.weight(),
                existing == null ? null : existing.getOwnerUuid(), existing == null ? null : existing.getSlot());
        return tags.create(tag).thenAccept(ok -> {
            if (!ok) errors.add(spec.name() + ": the database refused the tag (see the log)");
            else counts[existing == null ? 0 : 1]++;
        });
    }

    private static boolean matches(Tag t, Spec s, Material material) {
        return t.getDisplay().equals(s.display()) && t.getType() == s.type() && t.isPublic() == s.isPublic()
                && t.isColorable() == s.colorable() && t.getWeight() == s.weight()
                && t.getMaterial() != null && t.getMaterial().getType() == material;
    }
}
