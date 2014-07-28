package io.vertx.codegen;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Types;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Describes a java type.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class TypeInfo {

  public static TypeInfo create(Type type) {
    if (type == void.class) {
      return Void.INSTANCE;
    } else if (type instanceof java.lang.Class) {
      String fqcn = type.getTypeName();
      java.lang.Class classType = (java.lang.Class) type;
      if (classType.isPrimitive()) {
        return new Primitive(classType.getName());
      } else {
        return new Class(Helper.getKind(classType::getAnnotation, fqcn), fqcn);
      }
    } else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      List<TypeInfo> args = Arrays.asList(parameterizedType.getActualTypeArguments()).
          stream().
          map(TypeInfo::create).
          collect(Collectors.toList());
      java.lang.Class raw = (java.lang.Class) parameterizedType.getRawType();
      String fqcn = raw.getName();
      return new Parameterized(new Class(Helper.getKind(raw::getAnnotation, fqcn), fqcn), args);
    } else if (type instanceof java.lang.reflect.TypeVariable) {
      return new Variable(((java.lang.reflect.TypeVariable)type).getName());
    } else {
      throw new IllegalArgumentException("Unsupported type " + type);
    }
  }

  public static TypeInfo create(Types typeUtils, Iterable<DeclaredType> resolvingTypes, TypeMirror type) {
    switch (type.getKind()) {
      case VOID:
        return Void.INSTANCE;
      case DECLARED:
        return create(typeUtils, resolvingTypes, (DeclaredType) type);
      case DOUBLE:
      case LONG:
      case FLOAT:
      case CHAR:
      case BYTE:
      case SHORT:
      case BOOLEAN:
      case INT:
        return new Primitive(type.toString());
      case TYPEVAR:
        TypeMirror resolved = resolveTypeVariable(typeUtils, resolvingTypes, (TypeVariable) type);
        if (resolved instanceof TypeVariable) {
          return create(typeUtils, (TypeVariable) resolved);
        } else {
          return create(typeUtils, resolvingTypes, resolved);
        }
      case WILDCARD:
        return create(typeUtils, (WildcardType) type);
      default:
        throw new IllegalArgumentException("Illegal type " + type + " of kind " + type.getKind());
    }
  }

  private static TypeMirror resolveTypeVariable(Types typeUtils, Iterable<DeclaredType> resolvingTypes, TypeVariable type) {
    for (DeclaredType d : resolvingTypes) {
      TypeMirror tm;
      try {
        tm = typeUtils.asMemberOf(d, type.asElement());
      } catch (java.lang.IllegalArgumentException ignore) {
        ignore.printStackTrace();
        continue;
      }
      if (!typeUtils.isSameType(tm, type)) {
        if (tm instanceof TypeVariable) {
          type = (TypeVariable) tm;
        } else {
          return tm;
        }
      }
    }
    return type;
  }

  public static Wildcard create(Types typeUtils, WildcardType type) {
    if (type.getExtendsBound() != null) {
      throw new IllegalArgumentException("Wildcard type cannot have an upper bound");
    }
    if (type.getSuperBound() != null) {
      throw new IllegalArgumentException("Wildcard type cannot have a lower bound");
    }
    return new Wildcard();
  }

  /**
   * Simple wildcard without bound support.
   */
  public static class Wildcard extends TypeInfo {

    @Override
    public boolean equals(Object obj) {
      return obj instanceof Wildcard;
    }

    @Override
    public String format(boolean qualified) {
      return "?";
    }
  }

  public static Variable create(Types typeUtils, TypeVariable type) {
    return new Variable(type.toString());
  }

  public static TypeInfo create(Types typeUtils, Iterable<DeclaredType> resolvingTypes, DeclaredType type) {
    String fqcn = typeUtils.erasure(type).toString();
    ClassKind kind = Helper.getKind(annotationType -> type.asElement().getAnnotation(annotationType), fqcn);
    Class raw = new Class(kind, fqcn);
    List<? extends TypeMirror> typeArgs = type.getTypeArguments();
    if (typeArgs.size() > 0) {
      List<TypeInfo> typeArguments;
      typeArguments = new ArrayList<>(typeArgs.size());
      for (TypeMirror typeArg : typeArgs) {
        TypeInfo typeArgDesc = create(typeUtils, resolvingTypes, typeArg);
        // Need to check it is an interface type
        typeArguments.add(typeArgDesc);
      }
      return new Parameterized(raw, typeArguments);
    } else {
      return raw;
    }
  }

  public static class Primitive extends TypeInfo {

    final String name;

    public Primitive(String name) {
      this.name = name;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Primitive) {
        return name.equals(((Primitive) obj).name);
      }
      return false;
    }

    @Override
    public ClassKind getKind() {
      return ClassKind.PRIMITIVE;
    }

    @Override
    public String format(boolean qualified) {
      return name;
    }
  }

  public static class Variable extends TypeInfo {

    final String name;

    public Variable(String name) {
      this.name = name;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Variable) {
        Variable that = (Variable) obj;
        return name.equals(that.name);
      } else {
        return false;
      }
    }

    @Override
    public TypeInfo getErased() {
      return new Class(ClassKind.OBJECT, java.lang.Object.class.getName());
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public String format(boolean qualified) {
      return name;
    }

    @Override
    public ClassKind getKind() {
      return ClassKind.OBJECT;
    }

  }

  public static class Parameterized extends TypeInfo {

    final Class raw;
    final List<TypeInfo> args;

    public Parameterized(Class raw, List<TypeInfo> args) {
      this.raw = raw;
      this.args = args;
    }

    @Override
    public TypeInfo getErased() {
      return new Parameterized(raw, args.stream().map(TypeInfo::getErased).collect(Collectors.toList()));
    }

    public Class getRaw() {
      return raw;
    }

    // To remove : when fully migrated
    public List<TypeInfo> getTypeArguments() {
      return args;
    }

    public List<TypeInfo> getArgs() {
      return args;
    }

    @Override
    public ClassKind getKind() {
      return raw.getKind();
    }

    @Override
    public void collectImports(Collection<TypeInfo.Class> imports) {
      raw.collectImports(imports);
      args.stream().forEach(a -> a.collectImports(imports));
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Parameterized) {
        Parameterized that = (Parameterized) obj;
        return raw.equals(that.raw) && args.equals(that.args);
      }
      return false;
    }

    @Override
    public String format(boolean qualified) {
      StringBuilder buf = new StringBuilder(raw.format(qualified)).append('<');
      for (int i = 0;i < args.size();i++) {
        TypeInfo typeArgument = args.get(i);
        if (i > 0) {
          buf.append(',');
        }
        buf.append(typeArgument.format(qualified));
      }
      buf.append('>');
      return buf.toString();
    }

    @Override
    public TypeInfo renamePackage(String oldPackageName, String newPackageName) {
      return new Parameterized(
          raw.renamePackage(oldPackageName, newPackageName),
          args.stream().map(typeArgument -> typeArgument.renamePackage(oldPackageName, newPackageName)).
              collect(Collectors.toList()));
    }
  }

  public static class Class extends TypeInfo {

    final ClassKind kind;
    final String fqcn;
    final String simpleName;
    final String packageName;

    public Class(ClassKind kind, String fqcn) {
      this.kind = kind;
      this.fqcn = fqcn;
      this.simpleName = Helper.getSimpleName(fqcn);
      this.packageName = Helper.getPackageName(fqcn);
    }

    public ClassKind getKind() {
      return kind;
    }

    public String getPackageName() {
      return packageName;
    }

    @Override
    public void collectImports(Collection<TypeInfo.Class> imports) {
      imports.add(this);
    }

    @Override
    public TypeInfo.Class renamePackage(String oldPackageName, String newPackageName) {
      return packageName.startsWith(oldPackageName) ?
          new Class(kind, newPackageName + fqcn.substring(oldPackageName.length())) :
          this;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Class) {
        return fqcn.equals(((Class) obj).fqcn);
      }
      return false;
    }

    @Override
    public String format(boolean qualified) {
      return qualified ? fqcn : simpleName;
    }
  }

  public static class Void extends TypeInfo {
    public static TypeInfo INSTANCE = new Void() {};
    private Void() {}
    @Override
    public boolean equals(Object obj) {
      return obj instanceof Void;
    }
    @Override
    public String format(boolean qualified) {
      return "void";
    }
  }

  public abstract boolean equals(Object obj);

  public int hashCode() {
    return toString().hashCode();
  }

  /**
   * Collect the import fqcn needed by this type.
   *
   * @param imports the imports
   */
  public void collectImports(Collection<TypeInfo.Class> imports) {
  }

  /**
   * @return the raw type of this type
   */
  public TypeInfo getErased() {
    return this;
  }

  /**
   * @return the class kind this type resolves to
   */
  public ClassKind getKind() {
    return ClassKind.OTHER;
  }

  /**
   * @return the declaration suitable for source code represented using qualified names, for instance
   * <code>io.vertx.core.Handler&lt;io.vertx.core.buffer.Buffer&gt;</code>
   */
  public String getName() {
    return format(true);
  }

  public TypeInfo renamePackage(String oldPackageName, String newPackageName) {
    return this;
  }

  /**
   * @return the declaration suitable for source code represented using unqualified names, for instance
   * <code>Handler&lt;Buffer&gt;</code>
   */
  public String getSimpleName() { return format(false); }

  /**
   * @return the @{link #getName} value of this type
   */
  public String toString() {
    return getName();
  }

  /**
   * Renders the type name.
   *
   * @param qualified true when class fqcn should be used, otherwise simple names will be used
   * @return the representation of the type
   */
  abstract String format(boolean qualified);

}
