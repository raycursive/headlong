/*
   Copyright 2019 Evan Saulpaugh

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package com.esaulpaugh.headlong.abi;

import com.esaulpaugh.headlong.util.Integers;
import com.esaulpaugh.headlong.util.Strings;
import com.esaulpaugh.headlong.util.SuperSerial;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;

import static com.esaulpaugh.headlong.abi.Encoding.OFFSET_LENGTH_BYTES;
import static com.esaulpaugh.headlong.abi.TupleType.countBytes;
import static com.esaulpaugh.headlong.abi.TupleType.totalLen;
import static com.esaulpaugh.headlong.abi.UnitType.UNIT_LENGTH_BYTES;

/**
 * Represents static array types such as bytes3 or uint16[3][2] and dynamic array types such as decimal[5][] or
 * string[4].
 *
 * @param <E> the {@link ABIType} for the elements of the array
 * @param <J> this {@link ArrayType}'s corresponding Java type
 */
public final class ArrayType<E extends ABIType<?>, J> extends ABIType<J> {

    private static final ClassLoader CLASS_LOADER = Thread.currentThread().getContextClassLoader();

    static final Class<String> STRING_CLASS = String.class;
    static final Class<String[]> STRING_ARRAY_CLASS = String[].class;

    public static final int DYNAMIC_LENGTH = -1;

    private final boolean isString;
    private final E elementType;
    private final int length;
    private final Class<?> arrayClass;
    private final int staticByteLen;

    ArrayType(String canonicalType, Class<J> clazz, E elementType, int length, Class<?> arrayClass) {
        super(canonicalType, clazz, DYNAMIC_LENGTH == length || elementType.dynamic);
        this.isString = STRING_CLASS == clazz;
        this.elementType = elementType;
        this.length = length;
        this.arrayClass = arrayClass;
        this.staticByteLen = dynamic ? OFFSET_LENGTH_BYTES : staticArrLen(this);
    }

    public E getElementType() {
        return elementType;
    }

    public int getLength() {
        return length;
    }

    public boolean isString() {
        return isString;
    }

    @Override
    Class<?> arrayClass() {
        if(arrayClass != null) {
            return arrayClass;
        }
        try {
            return Class.forName('[' + clazz.getName(), false, CLASS_LOADER);
        } catch (ClassNotFoundException cnfe) {
            throw new AssertionError(cnfe);
        }
    }

    @Override
    public int typeCode() {
        return TYPE_CODE_ARRAY;
    }

    @Override
    int staticByteLength() {
        return staticByteLen;
    }

    @Override
    int dynamicByteLength(Object value) {
        return totalLen(calcElementsLen(value), length == DYNAMIC_LENGTH);
    }

    @Override
    int byteLength(Object value) {
        if(!dynamic) return staticByteLen;
        return totalLen(calcElementsLen(value), length == DYNAMIC_LENGTH);
    }

    static int staticArrLen(ABIType<?> type) {
        int product = 1;
        ArrayType<?, ?> at;
        do {
            at = (ArrayType<?, ?>) type;
            final int len = at.getLength();
            product = product *
                    (at.getElementType() instanceof ByteType
                            ? Integers.roundLengthUp(len, UNIT_LENGTH_BYTES) / UNIT_LENGTH_BYTES
                            : len);
        } while((type = at.getElementType()) instanceof ArrayType<?, ?>);
        return product * (type instanceof UnitType ? UNIT_LENGTH_BYTES : TupleType.staticTupleLen(type));
    }

    private int calcElementsLen(Object value) {
        switch (elementType.typeCode()) {
        case TYPE_CODE_BOOLEAN: return ((boolean[]) value).length * UNIT_LENGTH_BYTES;
        case TYPE_CODE_BYTE: return Integers.roundLengthUp(byteCount(value), UNIT_LENGTH_BYTES);
        case TYPE_CODE_INT: return ((int[]) value).length * UNIT_LENGTH_BYTES;
        case TYPE_CODE_LONG: return ((long[]) value).length * UNIT_LENGTH_BYTES;
        case TYPE_CODE_BIG_INTEGER:
        case TYPE_CODE_BIG_DECIMAL: return ((Number[]) value).length * UNIT_LENGTH_BYTES;
        case TYPE_CODE_ARRAY:
        case TYPE_CODE_TUPLE: return measureByteLength((Object[]) value);
        case TYPE_CODE_ADDRESS: return ((Address[]) value).length * UNIT_LENGTH_BYTES;
        default: throw new AssertionError();
        }
    }

    private int staticByteLengthPacked() {
        if(length == DYNAMIC_LENGTH) {
            throw new IllegalArgumentException("array of dynamic elements");
        }
        return length * elementType.byteLengthPacked(null);
    }

    @Override
    int byteLengthPacked(Object value) {
        if(value == null) {
            return staticByteLengthPacked();
        }
        switch (elementType.typeCode()) {
        case TYPE_CODE_BOOLEAN: return ((boolean[]) value).length; // * 1
        case TYPE_CODE_BYTE: return byteCount(value); // * 1
        case TYPE_CODE_INT: return ((int[]) value).length * elementType.byteLengthPacked(null);
        case TYPE_CODE_LONG: return ((long[]) value).length * elementType.byteLengthPacked(null);
        case TYPE_CODE_BIG_INTEGER:
        case TYPE_CODE_BIG_DECIMAL: return ((Number[]) value).length * elementType.byteLengthPacked(null);
        case TYPE_CODE_ARRAY:
        case TYPE_CODE_TUPLE:
        case TYPE_CODE_ADDRESS: return measureByteLengthPacked((Object[]) value);
        default: throw new AssertionError();
        }
    }

    private int byteCount(Object value) {
        return decodeIfString(value).length;
    }

    private byte[] decodeIfString(Object value) {
        return !isString ? (byte[]) value : Strings.decode((String) value, Strings.UTF_8);
    }

    Object encodeIfString(byte[] bytes) {
        return !isString ? bytes : Strings.encode(bytes, Strings.UTF_8);
    }

    @Override
    public int validate(J value) {
        return totalLen(validateElements(validateClass(value)), length == DYNAMIC_LENGTH); // validateClass to disallow Object[] etc
    }

    private int validateElements(J value) {
        switch (elementType.typeCode()) {
        case TYPE_CODE_BOOLEAN: return validateBooleans((boolean[]) value);
        case TYPE_CODE_BYTE: return validateBytes(value);
        case TYPE_CODE_INT: return validateInts((int[]) value, (IntType) elementType);
        case TYPE_CODE_LONG: return validateLongs((long[]) value, (LongType) elementType);
        case TYPE_CODE_BIG_INTEGER:
        case TYPE_CODE_BIG_DECIMAL:
        case TYPE_CODE_ARRAY:
        case TYPE_CODE_TUPLE:
        case TYPE_CODE_ADDRESS: return validateObjects((Object[]) value);
        default: throw new AssertionError();
        }
    }

    private int validateBooleans(boolean[] arr) {
        return checkLength(arr.length, arr) * UNIT_LENGTH_BYTES;
    }

    private int validateBytes(J arr) {
        return Integers.roundLengthUp(checkLength(byteCount(arr), arr), UNIT_LENGTH_BYTES);
    }

    private int offsetsLen(int len) {
        return elementType.dynamic ? OFFSET_LENGTH_BYTES * len : 0;
    }

    private int validateInts(int[] arr, IntType type) {
        return countBytes(true, checkLength(arr.length, arr), offsetsLen(arr.length), i -> type.validatePrimitive(arr[i]));
    }

    private int validateLongs(long[] arr, LongType type) {
        return countBytes(true, checkLength(arr.length, arr), offsetsLen(arr.length), i -> type.validatePrimitive(arr[i]));
    }

    private int validateObjects(Object[] arr) {
        return countBytes(true, checkLength(arr.length, arr), offsetsLen(arr.length), i -> elementType._validate(arr[i]));
    }

    private int measureByteLength(Object[] arr) {
        return countBytes(true, arr.length, offsetsLen(arr.length), i -> elementType.byteLength(arr[i]));
    }

    private int measureByteLengthPacked(Object[] arr) { // don't count offsets
        return countBytes(true, arr.length, 0, i -> elementType.byteLengthPacked(arr[i]));
    }

    private int checkLength(final int valueLen, Object value) {
        if(length != DYNAMIC_LENGTH && length != valueLen) {
            throw mismatchErr("array length",
                    friendlyClassName(value.getClass(), valueLen), friendlyClassName(clazz, length),
                    "length " + length, "" + valueLen);
        }
        return valueLen;
    }

    @Override
    void encodeTail(Object value, ByteBuffer dest) {
        switch (elementType.typeCode()) {
        case TYPE_CODE_BOOLEAN: encodeBooleans((boolean[]) value, dest); return;
        case TYPE_CODE_BYTE: encodeBytes(decodeIfString(value), dest); return;
        case TYPE_CODE_INT: encodeInts((int[]) value, dest); return;
        case TYPE_CODE_LONG: encodeLongs((long[]) value, dest); return;
        case TYPE_CODE_BIG_INTEGER:
        case TYPE_CODE_BIG_DECIMAL:
        case TYPE_CODE_ARRAY:
        case TYPE_CODE_TUPLE:
        case TYPE_CODE_ADDRESS: encodeObjects((Object[]) value, dest); return;
        default: throw new AssertionError();
        }
    }

    private void encodeObjects(Object[] arr, ByteBuffer dest) {
        encodeArrayLen(arr.length, dest);
        TupleType.encodeObjects(dynamic, arr, i -> elementType, dest, OFFSET_LENGTH_BYTES * arr.length);
    }

    private void encodeArrayLen(int len, ByteBuffer dest) {
        if(length == DYNAMIC_LENGTH) {
            Encoding.insertIntUnsigned(len, dest);
        }
    }

    private void encodeBooleans(boolean[] arr, ByteBuffer dest) {
        encodeArrayLen(arr.length, dest);
        for (boolean e : arr) {
            BooleanType.encodeBoolean(e, dest);
        }
    }

    private void encodeBytes(byte[] arr, ByteBuffer dest) {
        encodeArrayLen(arr.length, dest);
        dest.put(arr);
        int rem = Integers.mod(arr.length, UNIT_LENGTH_BYTES);
        Encoding.insert00Padding(rem != 0 ? UNIT_LENGTH_BYTES - rem : 0, dest);
    }

    private void encodeInts(int[] arr, ByteBuffer dest) {
        encodeArrayLen(arr.length, dest);
        for (int e : arr) {
            Encoding.insertInt(e, dest);
        }
    }

    private void encodeLongs(long[] arr, ByteBuffer dest) {
        encodeArrayLen(arr.length, dest);
        for (long e : arr) {
            Encoding.insertInt(e, dest);
        }
    }

    @Override
    void encodePackedUnchecked(J value, ByteBuffer dest) {
        switch (elementType.typeCode()) {
        case TYPE_CODE_BOOLEAN: encodeBooleansPacked((boolean[]) value, dest); return;
        case TYPE_CODE_BYTE: dest.put(decodeIfString(value)); return;
        case TYPE_CODE_INT: encodeIntsPacked((int[]) value, elementType.byteLengthPacked(null), dest); return;
        case TYPE_CODE_LONG: encodeLongsPacked((long[]) value, elementType.byteLengthPacked(null), dest); return;
        case TYPE_CODE_BIG_INTEGER:
        case TYPE_CODE_BIG_DECIMAL:
        case TYPE_CODE_ARRAY:
        case TYPE_CODE_TUPLE:
        case TYPE_CODE_ADDRESS:
            for(Object e : (Object[]) value) {
                elementType.encodeObjectPackedUnchecked(e, dest);
            }
            return;
        default: throw new AssertionError();
        }
    }

    private static void encodeBooleansPacked(boolean[] arr, ByteBuffer dest) {
        for (boolean bool : arr) {
            BooleanType.encodeBooleanPacked(bool, dest);
        }
    }

    private static void encodeIntsPacked(int[] arr, int byteLen, ByteBuffer dest) {
        for (int e : arr) {
            LongType.encodeLong(e, byteLen, dest);
        }
    }

    private static void encodeLongsPacked(long[] arr, int byteLen, ByteBuffer dest) {
        for (long e : arr) {
            LongType.encodeLong(e, byteLen, dest);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    J decode(ByteBuffer bb, byte[] unitBuffer) {
        final int arrayLen = length == DYNAMIC_LENGTH ? Encoding.UINT17.decode(bb, unitBuffer) : length;
        switch (elementType.typeCode()) {
        case TYPE_CODE_BOOLEAN: return (J) decodeBooleans(arrayLen, bb, unitBuffer);
        case TYPE_CODE_BYTE: return (J) decodeBytes(arrayLen, bb);
        case TYPE_CODE_INT: return (J) decodeInts(arrayLen, bb, (IntType) elementType, unitBuffer);
        case TYPE_CODE_LONG: return (J) decodeLongs(arrayLen, bb, (LongType) elementType, unitBuffer);
        case TYPE_CODE_BIG_INTEGER:
        case TYPE_CODE_BIG_DECIMAL:
        case TYPE_CODE_ARRAY:
        case TYPE_CODE_TUPLE:
        case TYPE_CODE_ADDRESS: return (J) decodeObjects(arrayLen, bb, unitBuffer);
        default: throw new AssertionError();
        }
    }

    private static Object decodeBooleans(int len, ByteBuffer bb, byte[] unitBuffer) {
        final boolean[] booleans = new boolean[len]; // elements are false by default
        final int valOffset = UNIT_LENGTH_BYTES - Byte.BYTES;
        for(int i = 0; i < len; i++) {
            bb.get(unitBuffer);
            for (int j = 0; j < valOffset; j++) {
                if (unitBuffer[j] != Encoding.ZERO_BYTE) {
                    throw new IllegalArgumentException("illegal boolean value @ " + (bb.position() - UNIT_LENGTH_BYTES));
                }
            }
            switch (unitBuffer[valOffset]) {
            case 1: booleans[i] = true;
            case 0: continue;
            default: throw new IllegalArgumentException("illegal boolean value @ " + (bb.position() - UNIT_LENGTH_BYTES));
            }
        }
        return booleans;
    }

    private Object decodeBytes(int len, ByteBuffer bb) {
        byte[] data = new byte[len];
        bb.get(data);
        int mod = Integers.mod(len, UNIT_LENGTH_BYTES);
        if(mod != 0) {
            byte[] padding = new byte[UNIT_LENGTH_BYTES - mod];
            bb.get(padding);
            for (byte b : padding) {
                if(b != Encoding.ZERO_BYTE) throw new IllegalArgumentException("malformed array: non-zero padding byte");
            }
        }
        return encodeIfString(data);
    }

    private static Object decodeInts(int len, ByteBuffer bb, IntType intType, byte[] unitBuffer) {
        int[] ints = new int[len];
        for (int i = 0; i < len; i++) {
            ints[i] = intType.decode(bb, unitBuffer);
        }
        return ints;
    }

    private static Object decodeLongs(int len, ByteBuffer bb, LongType longType, byte[] unitBuffer) {
        long[] longs = new long[len];
        for (int i = 0; i < len; i++) {
            longs[i] = longType.decode(bb, unitBuffer);
        }
        return longs;
    }

    private Object decodeObjects(int len, ByteBuffer bb, byte[] unitBuffer) {
        Object[] elements = (Object[]) Array.newInstance(elementType.clazz, len); // reflection ftw
        TupleType.decodeObjects(bb, unitBuffer, i -> elementType, elements);
        return elements;
    }

    /**
     * Parses RLP Object {@link com.esaulpaugh.headlong.rlp.util.Notation} as a {@link J}.
     *
     * @param s the array's RLP object notation
     * @return  the parsed array
     * @see com.esaulpaugh.headlong.rlp.util.Notation
     */
    @Override
    @SuppressWarnings("unchecked")
    public J parseArgument(String s) { // expects RLP object notation such as "['00', '01', '01']"
        return (J) SuperSerial.deserializeArray(this, s, false);
    }

    public static ABIType<?> baseType(ABIType<?> type) {
        while (type.typeCode() == ABIType.TYPE_CODE_ARRAY) {
            type = ((ArrayType<? extends ABIType<?>, ?>) type).getElementType();
        }
        return type;
    }
}
