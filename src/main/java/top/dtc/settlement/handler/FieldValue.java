package top.dtc.settlement.handler;

import java.util.function.Supplier;

public class FieldValue<T> {

    private static final FieldValue<?> EMPTY = new FieldValue<>();

    public boolean exist = false;

    public T value;

    private FieldValue() {}

    public FieldValue(T value) {
        this.value = value;
        this.exist = true;
    }

    public static<T> FieldValue<T> empty() {
        return (FieldValue<T>) EMPTY;
    }

    public T orElseGet(Supplier<? extends T> other) {
        return value != null ? value : other.get();
    }
}
