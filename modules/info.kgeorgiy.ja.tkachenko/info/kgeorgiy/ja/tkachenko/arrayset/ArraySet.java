package info.kgeorgiy.ja.tkachenko.arrayset;

import java.util.*;

public class ArraySet<T> extends AbstractSet<T> implements SortedSet<T> {
    private final List<T> data;
    private final Comparator<? super T> comparator;

    public ArraySet() {
        this(List.of(), null);
    }

    public ArraySet(final Collection<? extends T> data, final Comparator<? super T> comparator) {
        final TreeSet<T> treeSet = new TreeSet<>(comparator);
        treeSet.addAll(data);
        this.data = List.copyOf(treeSet);
        this.comparator = comparator;
    }

    public ArraySet(final Collection<? extends T> data) {
        this(data, null);
    }

    public ArraySet(final Comparator<? super T> comparator) {
        this(List.of(), comparator);
    }

    public ArraySet(final SortedSet<T> sortedSet) {
        this.data = List.copyOf(sortedSet);
        this.comparator = sortedSet.comparator();
    }

    private ArraySet(final List<T> data, final Comparator<? super T> comparator) {
        this.data = data;
        this.comparator = comparator;
    }

    @Override
    public Comparator<? super T> comparator() {
        return comparator;
    }

    @Override
    public SortedSet<T> subSet(final T t, final T e1) {
        if (compare(t, e1) > 0) {
            throw new IllegalArgumentException("Left bound is greater than right bound");
        }
        return subSetImpl(t, e1, false);
    }

    @SuppressWarnings("unchecked")
    private int compare(final T t, final T e1) {
        if (comparator != null) {
            return comparator.compare(t, e1);
        } else {
            return ((Comparable<? super T>) t).compareTo(e1);
        }
    }

    @Override
    public SortedSet<T> headSet(final T t) {
        if (data.isEmpty()) {
            return new ArraySet<>(comparator);
        }
        return subSetImpl(first(), t, false);
    }

    @Override
    public SortedSet<T> tailSet(final T t) {
        if (data.isEmpty()) {
            return new ArraySet<>(comparator);
        }
        return subSetImpl(t, last(), true);
    }

    private SortedSet<T> subSetImpl(final T t, final T e1, final boolean inclusive) {
        final int left = getIndex(t);
        int right = getIndex(e1);
        if (inclusive) {
            right++;
        }
        if (left >= right) {
            return new ArraySet<>(comparator);
        }
        return new ArraySet<>(data.subList(left, right), comparator);
    }

    private int getIndex(final T t) {
        int res = Collections.binarySearch(data, t, comparator);
        if (res < 0) {
            res = -res - 1;
        }
        return res;
    }

    private void checkEmpty() {
        if (data.isEmpty()) {
            throw new NoSuchElementException("ArraySet is empty");
        }
    }

    @Override
    public T first() {
        checkEmpty();
        return data.get(0);
    }

    @Override
    public T last() {
        checkEmpty();
        return data.get(size() - 1);
    }

    @Override
    public int size() {
        return data.size();
    }

    @Override
    public boolean isEmpty() {
        return data.isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean contains(final Object o) {
        final T t = (T) o;
        return Collections.binarySearch(data, t, comparator) >= 0;
    }

    @Override
    public Iterator<T> iterator() {
        return Collections.unmodifiableList(data).iterator();
    }
}
