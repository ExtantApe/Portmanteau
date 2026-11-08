package com.extantape.portmanteau.sorting.category;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Every category, in file order, plus a catch-all at the end for anything no file names, so an item added in a
 * Minecraft update still sorts.
 */
public final class Categories {
    public static final String CATCH_ALL = "everything-else";

    /** Where an item falls: its category's position, and its line inside the category. */
    public record Placement(int category, int line) {}

    private final List<CategoryFile> files;

    public Categories(List<CategoryFile> files) {
        List<CategoryFile> sorted = new ArrayList<>(files);
        sorted.sort(Comparator.comparingInt(CategoryFile::number).thenComparing(CategoryFile::fileName));
        this.files = List.copyOf(sorted);
    }

    public List<CategoryFile> files() {
        return files;
    }

    /** The number of categories, counting the catch-all. */
    public int size() {
        return files.size() + 1;
    }

    public Placement place(String path, Set<String> tags) {
        for (int i = 0; i < files.size(); i++) {
            int line = files.get(i).match(path, tags);
            if (line >= 0) return new Placement(i, line);
        }
        return new Placement(files.size(), 0);
    }

    public int indexOf(String id) {
        if (CATCH_ALL.equals(id)) return files.size();
        for (int i = 0; i < files.size(); i++) {
            if (files.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    public Optional<CategoryFile> byId(String id) {
        int i = indexOf(id);
        return i >= 0 && i < files.size() ? Optional.of(files.get(i)) : Optional.empty();
    }

    public boolean sticky(int category) {
        return category < files.size() && files.get(category).sticky();
    }

    public String display(int category) {
        return category < files.size() ? files.get(category).display() : "Everything else";
    }

    public String id(int category) {
        return category < files.size() ? files.get(category).id() : CATCH_ALL;
    }
}
