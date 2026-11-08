package com.extantape.portmanteau.sorting.order;

import java.util.List;

/**
 * How to order one section: a sorting method, plus categories a player moved to the front, in their order. The other
 * categories keep their file order after them.
 */
public record OrderSpec(SortMethod method, List<Integer> priorities) {
    public OrderSpec {
        priorities = List.copyOf(priorities);
    }

    public static OrderSpec of(SortMethod method) {
        return new OrderSpec(method, List.of());
    }

    /** The category's rank in this order: front categories first, then the rest in file order. */
    public int rank(int category) {
        int i = priorities.indexOf(category);
        return i >= 0 ? i : priorities.size() + category;
    }
}
