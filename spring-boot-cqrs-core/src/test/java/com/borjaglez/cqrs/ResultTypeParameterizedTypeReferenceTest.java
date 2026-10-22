package com.borjaglez.cqrs;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

/**
 * The remote buses convert a {@link ResultType} with {@code ParameterizedTypeReference.forType}, so
 * both must capture the same type from the same class shape.
 */
class ResultTypeParameterizedTypeReferenceTest {

  @Test
  void capturesWhatParameterizedTypeReferenceCapturesFromAnAnonymousSubclass() {
    assertThat(ParameterizedTypeReference.forType(new ResultType<List<String>>() {}.type()))
        .isEqualTo(new ParameterizedTypeReference<List<String>>() {});
  }

  @Test
  void capturesWhatParameterizedTypeReferenceCapturesThroughAFixedIntermediateSubclass() {
    assertThat(ParameterizedTypeReference.forType(new ListOfStringsResult() {}.type()))
        .isEqualTo(new ListOfStringsReference() {});
  }

  @Test
  void capturesWhatParameterizedTypeReferenceCapturesThroughAGenericIntermediateSubclass() {
    Type captured = new ListOfResult<Integer>() {}.type();
    Type expected = new ListOfReference<Integer>() {}.getType();

    assertThat(captured.getTypeName())
        .isEqualTo(expected.getTypeName())
        .isEqualTo("java.util.List<E>");
    assertThat(((ParameterizedType) captured).getActualTypeArguments()[0])
        .isInstanceOf(TypeVariable.class);
    assertThat(((ParameterizedType) expected).getActualTypeArguments()[0])
        .isInstanceOf(TypeVariable.class);
  }

  static class ListOfStringsResult extends ResultType<List<String>> {}

  static class ListOfStringsReference extends ParameterizedTypeReference<List<String>> {}

  abstract static class ListOfResult<E> extends ResultType<List<E>> {}

  abstract static class ListOfReference<E> extends ParameterizedTypeReference<List<E>> {}
}
