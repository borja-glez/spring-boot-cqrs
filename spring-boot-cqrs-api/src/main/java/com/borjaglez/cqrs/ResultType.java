package com.borjaglez.cqrs;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

/**
 * The full type, type arguments included, of the result a caller expects from {@link
 * com.borjaglez.cqrs.command.CommandBus#dispatchAndReceive(com.borjaglez.cqrs.command.Command,
 * ResultType)} or {@link com.borjaglez.cqrs.query.QueryBus#ask(com.borjaglez.cqrs.query.Query,
 * ResultType)}.
 *
 * <p>A remote bus rebuilds a reply from its runtime class only, so a generic result such as a
 * {@code List<Order>} comes back with untyped elements unless the caller says what it expects.
 * Capture the type with an anonymous subclass, or wrap a {@link Type} with {@link #of(Type)}:
 *
 * <pre>{@code
 * List<Order> orders = queryBus.ask(new ListOrders(), new ResultType<List<Order>>() {});
 * }</pre>
 *
 * <p>Two instances are equal when they describe the same type.
 *
 * @param <T> the expected result type
 */
public abstract class ResultType<T> {

  private final Type type;

  /**
   * Captures the type argument of the anonymous subclass being created.
   *
   * @throws IllegalArgumentException if the subclass gives no type argument
   */
  protected ResultType() {
    if (!(getClass().getGenericSuperclass() instanceof ParameterizedType superclass)) {
      throw new IllegalArgumentException(
          "ResultType must be created with a type argument, e.g. new ResultType<List<Order>>() {}");
    }
    this.type = superclass.getActualTypeArguments()[0];
  }

  private ResultType(Type type) {
    this.type = Objects.requireNonNull(type, "type must not be null");
  }

  /** Returns a {@code ResultType} for {@code type}, built at runtime. */
  public static <T> ResultType<T> of(Type type) {
    return new Wrapped<>(type);
  }

  /** Returns the expected result type. */
  public final Type type() {
    return type;
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof ResultType<?> that && type.equals(that.type));
  }

  @Override
  public int hashCode() {
    return type.hashCode();
  }

  @Override
  public String toString() {
    return "ResultType<" + type.getTypeName() + ">";
  }

  private static final class Wrapped<T> extends ResultType<T> {

    private Wrapped(Type type) {
      super(type);
    }
  }
}
