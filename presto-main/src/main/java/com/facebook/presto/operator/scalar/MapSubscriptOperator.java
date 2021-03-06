/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.operator.scalar;

import com.facebook.presto.annotation.UsedByGeneratedCode;
import com.facebook.presto.metadata.BoundVariables;
import com.facebook.presto.metadata.FunctionRegistry;
import com.facebook.presto.metadata.SqlOperator;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.PrestoException;
import com.facebook.presto.spi.block.Block;
import com.facebook.presto.spi.block.SingleMapBlock;
import com.facebook.presto.spi.function.OperatorType;
import com.facebook.presto.spi.type.BooleanType;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.spi.type.TypeManager;
import com.facebook.presto.spi.type.VarcharType;
import com.facebook.presto.sql.FunctionInvoker;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Primitives;
import io.airlift.slice.Slice;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import static com.facebook.presto.metadata.Signature.internalOperator;
import static com.facebook.presto.metadata.Signature.typeVariable;
import static com.facebook.presto.spi.StandardErrorCode.GENERIC_INTERNAL_ERROR;
import static com.facebook.presto.spi.StandardErrorCode.INVALID_FUNCTION_ARGUMENT;
import static com.facebook.presto.spi.function.OperatorType.SUBSCRIPT;
import static com.facebook.presto.spi.type.TypeSignature.parseTypeSignature;
import static com.facebook.presto.spi.type.TypeUtils.readNativeValue;
import static com.facebook.presto.sql.relational.Signatures.castSignature;
import static com.facebook.presto.util.Reflection.methodHandle;
import static java.lang.String.format;

public class MapSubscriptOperator
        extends SqlOperator
{
    private static final MethodHandle METHOD_HANDLE_BOOLEAN = methodHandle(MapSubscriptOperator.class, "subscript", boolean.class, FunctionInvoker.class, MethodHandle.class, Type.class, Type.class, ConnectorSession.class, Block.class, boolean.class);
    private static final MethodHandle METHOD_HANDLE_LONG = methodHandle(MapSubscriptOperator.class, "subscript", boolean.class, FunctionInvoker.class, MethodHandle.class, Type.class, Type.class, ConnectorSession.class, Block.class, long.class);
    private static final MethodHandle METHOD_HANDLE_DOUBLE = methodHandle(MapSubscriptOperator.class, "subscript", boolean.class, FunctionInvoker.class, MethodHandle.class, Type.class, Type.class, ConnectorSession.class, Block.class, double.class);
    private static final MethodHandle METHOD_HANDLE_SLICE = methodHandle(MapSubscriptOperator.class, "subscript", boolean.class, FunctionInvoker.class, MethodHandle.class, Type.class, Type.class, ConnectorSession.class, Block.class, Slice.class);
    private static final MethodHandle METHOD_HANDLE_OBJECT = methodHandle(MapSubscriptOperator.class, "subscript", boolean.class, FunctionInvoker.class, MethodHandle.class, Type.class, Type.class, ConnectorSession.class, Block.class, Object.class);

    private final boolean legacyMissingKey;

    public MapSubscriptOperator(boolean legacyMissingKey)
    {
        super(SUBSCRIPT,
                ImmutableList.of(typeVariable("K"), typeVariable("V")),
                ImmutableList.of(),
                parseTypeSignature("V"),
                ImmutableList.of(parseTypeSignature("map(K,V)"), parseTypeSignature("K")));
        this.legacyMissingKey = legacyMissingKey;
    }

    @Override
    public ScalarFunctionImplementation specialize(BoundVariables boundVariables, int arity, TypeManager typeManager, FunctionRegistry functionRegistry)
    {
        Type keyType = boundVariables.getTypeVariable("K");
        Type valueType = boundVariables.getTypeVariable("V");

        MethodHandle keyEqualsMethod = functionRegistry.getScalarFunctionImplementation(internalOperator(OperatorType.EQUAL, BooleanType.BOOLEAN, ImmutableList.of(keyType, keyType))).getMethodHandle();

        MethodHandle methodHandle;
        if (keyType.getJavaType() == boolean.class) {
            methodHandle = METHOD_HANDLE_BOOLEAN;
        }
        else if (keyType.getJavaType() == long.class) {
            methodHandle = METHOD_HANDLE_LONG;
        }
        else if (keyType.getJavaType() == double.class) {
            methodHandle = METHOD_HANDLE_DOUBLE;
        }
        else if (keyType.getJavaType() == Slice.class) {
            methodHandle = METHOD_HANDLE_SLICE;
        }
        else {
            methodHandle = METHOD_HANDLE_OBJECT;
        }
        methodHandle = MethodHandles.insertArguments(methodHandle, 0, legacyMissingKey);
        FunctionInvoker functionInvoker = new FunctionInvoker(functionRegistry);
        methodHandle = methodHandle.bindTo(functionInvoker).bindTo(keyEqualsMethod).bindTo(keyType).bindTo(valueType);

        // this casting is necessary because otherwise presto byte code generator will generate illegal byte code
        if (valueType.getJavaType() == void.class) {
            methodHandle = methodHandle.asType(methodHandle.type().changeReturnType(void.class));
        }
        else {
            methodHandle = methodHandle.asType(methodHandle.type().changeReturnType(Primitives.wrap(valueType.getJavaType())));
        }

        return new ScalarFunctionImplementation(true, ImmutableList.of(false, false), methodHandle, isDeterministic());
    }

    @UsedByGeneratedCode
    public static Object subscript(boolean legacyMissingKey, FunctionInvoker functionInvoker, MethodHandle keyEqualsMethod, Type keyType, Type valueType, ConnectorSession session, Block map, boolean key)
    {
        if (map instanceof SingleMapBlock) {
            SingleMapBlock mapBlock = (SingleMapBlock) map;
            int valuePosition = mapBlock.seekKeyExact(key);
            if (valuePosition == -1) {
                if (legacyMissingKey) {
                    return null;
                }
                throw throwMissingKeyException(keyType, functionInvoker, key, session);
            }
            return readNativeValue(valueType, mapBlock, valuePosition);
        }
        // TODO: assume that map is always instanceof SingleMapBlock once all map producing code is updated.
        for (int position = 0; position < map.getPositionCount(); position += 2) {
            try {
                if ((boolean) keyEqualsMethod.invokeExact(keyType.getBoolean(map, position), key)) {
                    return readNativeValue(valueType, map, position + 1); // position + 1: value position
                }
            }
            catch (Throwable t) {
                Throwables.propagateIfInstanceOf(t, Error.class);
                Throwables.propagateIfInstanceOf(t, PrestoException.class);
                throw new PrestoException(GENERIC_INTERNAL_ERROR, t);
            }
        }
        if (legacyMissingKey) {
            return null;
        }
        throw throwMissingKeyException(keyType, functionInvoker, key, session);
    }

    @UsedByGeneratedCode
    public static Object subscript(boolean legacyMissingKey, FunctionInvoker functionInvoker, MethodHandle keyEqualsMethod, Type keyType, Type valueType, ConnectorSession session, Block map, long key)
    {
        if (map instanceof SingleMapBlock) {
            SingleMapBlock mapBlock = (SingleMapBlock) map;
            int valuePosition = mapBlock.seekKeyExact(key);
            if (valuePosition == -1) {
                if (legacyMissingKey) {
                    return null;
                }
                throw throwMissingKeyException(keyType, functionInvoker, key, session);
            }
            return readNativeValue(valueType, mapBlock, valuePosition);
        }
        // TODO: assume that map is always instanceof SingleMapBlock once all map producing code is updated.
        for (int position = 0; position < map.getPositionCount(); position += 2) {
            try {
                if ((boolean) keyEqualsMethod.invokeExact(keyType.getLong(map, position), key)) {
                    return readNativeValue(valueType, map, position + 1); // position + 1: value position
                }
            }
            catch (Throwable t) {
                Throwables.propagateIfInstanceOf(t, Error.class);
                Throwables.propagateIfInstanceOf(t, PrestoException.class);
                throw new PrestoException(GENERIC_INTERNAL_ERROR, t);
            }
        }
        if (legacyMissingKey) {
            return null;
        }
        throw throwMissingKeyException(keyType, functionInvoker, key, session);
    }

    @UsedByGeneratedCode
    public static Object subscript(boolean legacyMissingKey, FunctionInvoker functionInvoker, MethodHandle keyEqualsMethod, Type keyType, Type valueType, ConnectorSession session, Block map, double key)
    {
        if (map instanceof SingleMapBlock) {
            SingleMapBlock mapBlock = (SingleMapBlock) map;
            int valuePosition = mapBlock.seekKeyExact(key);
            if (valuePosition == -1) {
                if (legacyMissingKey) {
                    return null;
                }
                throw throwMissingKeyException(keyType, functionInvoker, key, session);
            }
            return readNativeValue(valueType, mapBlock, valuePosition);
        }
        // TODO: assume that map is always instanceof SingleMapBlock once all map producing code is updated.
        for (int position = 0; position < map.getPositionCount(); position += 2) {
            try {
                if ((boolean) keyEqualsMethod.invokeExact(keyType.getDouble(map, position), key)) {
                    return readNativeValue(valueType, map, position + 1); // position + 1: value position
                }
            }
            catch (Throwable t) {
                Throwables.propagateIfInstanceOf(t, Error.class);
                Throwables.propagateIfInstanceOf(t, PrestoException.class);
                throw new PrestoException(GENERIC_INTERNAL_ERROR, t);
            }
        }
        if (legacyMissingKey) {
            return null;
        }
        throw throwMissingKeyException(keyType, functionInvoker, key, session);
    }

    @UsedByGeneratedCode
    public static Object subscript(boolean legacyMissingKey, FunctionInvoker functionInvoker, MethodHandle keyEqualsMethod, Type keyType, Type valueType, ConnectorSession session, Block map, Slice key)
    {
        if (map instanceof SingleMapBlock) {
            SingleMapBlock mapBlock = (SingleMapBlock) map;
            int valuePosition = mapBlock.seekKeyExact(key);
            if (valuePosition == -1) {
                if (legacyMissingKey) {
                    return null;
                }
                throw throwMissingKeyException(keyType, functionInvoker, key, session);
            }
            return readNativeValue(valueType, mapBlock, valuePosition);
        }
        // TODO: assume that map is always instanceof SingleMapBlock once all map producing code is updated.
        for (int position = 0; position < map.getPositionCount(); position += 2) {
            try {
                if ((boolean) keyEqualsMethod.invokeExact(keyType.getSlice(map, position), key)) {
                    return readNativeValue(valueType, map, position + 1); // position + 1: value position
                }
            }
            catch (Throwable t) {
                Throwables.propagateIfInstanceOf(t, Error.class);
                Throwables.propagateIfInstanceOf(t, PrestoException.class);
                throw new PrestoException(GENERIC_INTERNAL_ERROR, t);
            }
        }
        if (legacyMissingKey) {
            return null;
        }
        throw throwMissingKeyException(keyType, functionInvoker, key, session);
    }

    @UsedByGeneratedCode
    public static Object subscript(boolean legacyMissingKey, FunctionInvoker functionInvoker, MethodHandle keyEqualsMethod, Type keyType, Type valueType, ConnectorSession session, Block map, Object key)
    {
        if (map instanceof SingleMapBlock) {
            SingleMapBlock mapBlock = (SingleMapBlock) map;
            int valuePosition = mapBlock.seekKeyExact((Block) key);
            if (valuePosition == -1) {
                if (legacyMissingKey) {
                    return null;
                }
                throw throwMissingKeyException(keyType, functionInvoker, key, session);
            }
            return readNativeValue(valueType, mapBlock, valuePosition);
        }
        // TODO: assume that map is always instanceof SingleMapBlock once all map producing code is updated.
        for (int position = 0; position < map.getPositionCount(); position += 2) {
            try {
                if ((boolean) keyEqualsMethod.invoke(keyType.getObject(map, position), key)) {
                    return readNativeValue(valueType, map, position + 1); // position + 1: value position
                }
            }
            catch (Throwable t) {
                Throwables.propagateIfInstanceOf(t, Error.class);
                Throwables.propagateIfInstanceOf(t, PrestoException.class);
                throw new PrestoException(GENERIC_INTERNAL_ERROR, t);
            }
        }
        if (legacyMissingKey) {
            return null;
        }
        throw throwMissingKeyException(keyType, functionInvoker, key, session);
    }

    private static RuntimeException throwMissingKeyException(Type type, FunctionInvoker functionInvoker, Object value, ConnectorSession session)
    {
        String stringValue;
        try {
            stringValue = ((Slice) functionInvoker.invoke(castSignature(VarcharType.VARCHAR, type), session, value)).toStringUtf8();
        }
        catch (RuntimeException e) {
            throw new PrestoException(INVALID_FUNCTION_ARGUMENT, "Key not present in map");
        }
        throw new PrestoException(INVALID_FUNCTION_ARGUMENT, format("Key not present in map: %s", stringValue));
    }
}
