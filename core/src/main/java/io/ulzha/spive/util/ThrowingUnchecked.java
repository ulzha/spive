package io.ulzha.spive.util;

import java.util.function.Function;

/** Helper for less verbose exception handling when following crash-only principles. */
// https://dzone.com/articles/how-to-handle-checked-exception-in-lambda-expressi
// https://stackoverflow.com/questions/39719370/how-to-wrap-checked-exceptions-but-keep-the-original-runtime-exceptions-in-java
// @FunctionalInterface
// interface ThrowingFunction<T, R, E extends Throwable> {
//  R apply(T t) throws E;
//  static <T, R, E extends Throwable> Function<T, R> unchecked(ThrowingFunction<T, R, E> f) {
//    return t -> {
//      try {
//        return f.apply(t);
//      } catch (Throwable e) {
//        throw new RuntimeException(e);
//      }
//    };
//  }
// }

// Alternative inspiration in
// https://stackoverflow.com/questions/49705335/throwing-checked-exceptions-with-completablefuture
// Completions?

public class ThrowingUnchecked<T, R, E extends Throwable>
    implements Function<T, R> /*extends ThrowingFunction<T, R, E>*/ {
  private final Function<T, R> delegate;

  public ThrowingUnchecked(final Function<T, R> delegate) {
    this.delegate = delegate;
  }

  @SuppressWarnings("unchecked")
  private static <T extends Throwable> RuntimeException sneakyThrow(Throwable t) throws T {
    throw (T) t; // rely on vacuous cast
  }

  @Override
  public R apply(final T t) {
    try {
      return delegate.apply(t);
    } catch (Exception e) {
      throw sneakyThrow(e);
    }
  }
}
