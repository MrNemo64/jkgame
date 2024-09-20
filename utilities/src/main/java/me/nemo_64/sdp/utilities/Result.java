package me.nemo_64.sdp.utilities;

public interface Result<V, E> {

    V value();

    E error();

    boolean isSuccessful();

    boolean isError();

    static <V, E> Result<V, E> ok(V value) {
        return new Success<>(value);
    }

    static <V, E> Result<V, E> err(E err) {
        return new Error<>(err);
    }

    final class Success<V, E> implements Result<V, E> {
        private final V value;

        private Success(V value) {
            this.value = value;
        }

        @Override
        public V value() {
            return value;
        }

        @Override
        public E error() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSuccessful() {
            return true;
        }

        @Override
        public boolean isError() {
            return false;
        }

        @Override
        public String toString() {
            return "Success{" +
                    "value=" + value +
                    '}';
        }
    }

    final class Error<V, E> implements Result<V, E> {
        private final E error;

        private Error(E error) {
            this.error = error;
        }

        @Override
        public V value() {
            throw new UnsupportedOperationException();
        }

        @Override
        public E error() {
            return error;
        }

        @Override
        public boolean isSuccessful() {
            return false;
        }

        @Override
        public boolean isError() {
            return true;
        }

        @Override
        public String toString() {
            return "Error{" +
                    "error=" + error +
                    '}';
        }
    }

}
