package com.borjaglez.cqrs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ResultTypeTest {

  @Test
  void capturesGenericTypeOfAnonymousSubclass() {
    Type type = new ResultType<List<String>>() {}.type();

    assertThat(type).isInstanceOf(ParameterizedType.class);
    ParameterizedType parameterized = (ParameterizedType) type;
    assertThat(parameterized.getRawType()).isEqualTo(List.class);
    assertThat(parameterized.getActualTypeArguments()).containsExactly(String.class);
  }

  @Test
  void capturesPlainClass() {
    assertThat(new ResultType<String>() {}.type()).isEqualTo(String.class);
  }

  @Test
  void capturesTypeThroughAFixedIntermediateSubclass() {
    Type listOfStrings = new ResultType<List<String>>() {}.type();

    assertThat(new ListOfStrings().type()).isEqualTo(listOfStrings);
    assertThat(new ListOfStrings() {}.type()).isEqualTo(listOfStrings);
  }

  @Test
  void capturesTheTypeArgumentOfTheClassThatExtendsResultType() {
    // As Spring's ParameterizedTypeReference does: a type variable of an intermediate subclass is
    // not resolved, whatever the anonymous subclass binds it to.
    Type type = new ListOf<Integer>() {}.type();

    assertThat(type).isInstanceOf(ParameterizedType.class);
    ParameterizedType parameterized = (ParameterizedType) type;
    assertThat(parameterized.getRawType()).isEqualTo(List.class);
    assertThat(parameterized.getActualTypeArguments()).hasSize(1);
    assertThat(parameterized.getActualTypeArguments()[0])
        .isEqualTo(ListOf.class.getTypeParameters()[0])
        .isInstanceOf(TypeVariable.class);
    assertThat(new MapOf<String, Integer>() {}.type().getTypeName())
        .isEqualTo("java.util.Map<K, V>");
  }

  @Test
  void ofWrapsAGivenType() {
    Type listOfStrings = new ResultType<List<String>>() {}.type();

    assertThat(ResultType.of(String.class).type()).isEqualTo(String.class);
    assertThat(ResultType.of(listOfStrings).type()).isSameAs(listOfStrings);
  }

  @Test
  void ofRejectsANullType() {
    assertThatThrownBy(() -> ResultType.of(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("type must not be null");
  }

  @Test
  @SuppressWarnings("rawtypes")
  void rejectsMissingTypeArgument() {
    assertThatThrownBy(() -> new ResultType() {})
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "ResultType must be created with a type argument, e.g. new ResultType<List<Order>>() {}");
    assertThatThrownBy(() -> new RawResultType() {})
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "ResultType must be created with a type argument, e.g. new ResultType<List<Order>>() {}");
  }

  @Test
  void equalTypesAreEqual() {
    ResultType<List<String>> captured = new ResultType<List<String>>() {};
    ResultType<List<String>> capturedAgain = new ResultType<List<String>>() {};
    ResultType<List<String>> wrapped = ResultType.of(captured.type());

    assertThat(captured).isEqualTo(captured).isEqualTo(capturedAgain).isEqualTo(wrapped);
    assertThat(captured)
        .hasSameHashCodeAs(capturedAgain)
        .hasSameHashCodeAs(wrapped)
        .isNotEqualTo(new ResultType<List<Integer>>() {})
        .isNotEqualTo(ResultType.of(String.class))
        .isNotEqualTo(captured.type())
        .isNotEqualTo(null);
  }

  @Test
  void toStringShowsTheType() {
    assertThat(new ResultType<List<String>>() {})
        .hasToString("ResultType<java.util.List<java.lang.String>>");
    assertThat(ResultType.of(String.class)).hasToString("ResultType<java.lang.String>");
  }

  static class ListOfStrings extends ResultType<List<String>> {}

  abstract static class ListOf<E> extends ResultType<List<E>> {}

  abstract static class MapOf<K, V> extends ResultType<Map<K, V>> {}

  @SuppressWarnings("rawtypes")
  abstract static class RawResultType extends ResultType {}
}
