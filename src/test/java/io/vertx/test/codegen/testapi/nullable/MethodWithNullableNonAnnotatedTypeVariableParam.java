package io.vertx.test.codegen.testapi.nullable;

import io.vertx.codegen.annotations.VertxGen;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface MethodWithNullableNonAnnotatedTypeVariableParam {

  <T> void method(T s);

}
