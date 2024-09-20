package me.nemo_64.sdp.utilities;

import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class IntReference implements IntSupplier, Supplier<Integer> {

    private int value;

    public static IntReference from(int value) {
        return new IntReference(value);
    }

    private IntReference(int value) {
        this.value = value;
    }

    public int getAndIncrement() {
        return getAndIncrement(1);
    }

    public int getAndIncrement(int amount) {
        int oldValue = this.value;
        this.value += amount;
        return oldValue;
    }

    public int incrementAndGet() {
        return incrementAndGet(1);
    }

    public int incrementAndGet(int amount) {
        this.value += amount;
        return this.value;
    }

    public int getAndDecrement() {
        return getAndDecrement(1);
    }

    public int getAndDecrement(int amount) {
        int oldValue = this.value;
        this.value -= amount;
        return oldValue;
    }

    public int decrementAndGet() {
        return decrementAndGet(1);
    }

    public int decrementAndGet(int amount) {
        this.value -= amount;
        return this.value;
    }

    public IntReference copy() {
        return new IntReference(this.value);
    }

    @Override
    public Integer get() {
        return value;
    }

    @Override
    public int getAsInt() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        IntReference that = (IntReference) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return get();
    }

    @Override
    public String toString() {
        return String.valueOf(get());
    }
}
