package com.borjaglez.cqrs;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import lombok.Generated;

/**
 * Utility for creating {@link MethodHandle} instances from reflected methods. The {@code
 * IllegalAccessException} catch block is excluded from JaCoCo coverage via {@code @Generated}
 * because it is unreachable in practice: {@link Method#setAccessible(boolean)} is called before
 * {@link MethodHandles.Lookup#unreflect(Method)}, and if {@code setAccessible(true)} succeeds then
 * {@code unreflect} will never throw {@code IllegalAccessException}.
 */
public final class MethodHandleUtil {

  private MethodHandleUtil() {}

  @Generated
  public static MethodHandle unreflect(Method method) {
    method.setAccessible(true);
    try {
      return MethodHandles.lookup().unreflect(method);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(
          "Failed to create MethodHandle for " + method.toGenericString(), e);
    }
  }
}
