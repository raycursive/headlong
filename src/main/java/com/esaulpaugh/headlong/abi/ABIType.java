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

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.IntFunction;

import static com.esaulpaugh.headlong.abi.UnitType.UNIT_LENGTH_BYTES;

/**
 * Represents a Contract ABI type such as uint256 or bytes. Used to validate, encode, and decode data.
 *
 * @param <J> this {@link ABIType}'s corresponding Java type
 */
public abstract class ABIType<J> {

    public static final int FLAGS_NONE = 0;
    /**
     * Experimental flag which enables an incompatible decode mode. Strongly consider using {@link #FLAGS_NONE} instead.
     * Behavior is subject to change or removal in future versions.
     */
    public static final int FLAG_LEGACY_DECODE = 1;
    static final int FLAGS_UNSET = 0x80000000;
    static final int OFFSET_LENGTH_BYTES = UNIT_LENGTH_BYTES;
    static final byte ZERO_BYTE = (byte) 0x00;
    static final byte ONE_BYTE = (byte) 0x01;

    private static final byte[] CACHED_ZERO_PADDING = new byte[UNIT_LENGTH_BYTES];
    private static final byte[] CACHED_NEG1_PADDING = new byte[UNIT_LENGTH_BYTES];

    static {
        Arrays.fill(CACHED_NEG1_PADDING, (byte) 0xFF);
    }

    public static final int TYPE_CODE_BOOLEAN = 0;
    public static final int TYPE_CODE_BYTE = 1;
    public static final int TYPE_CODE_INT = 2;
    public static final int TYPE_CODE_LONG = 3;
    public static final int TYPE_CODE_BIG_INTEGER = 4;
    public static final int TYPE_CODE_BIG_DECIMAL = 5;

    public static final int TYPE_CODE_ARRAY = 6;
    public static final int TYPE_CODE_TUPLE = 7;
    public static final int TYPE_CODE_ADDRESS = 8;

    public static final ABIType<?>[] EMPTY_ARRAY = new ABIType<?>[0];

    final String canonicalType;
    final Class<J> clazz;
    final boolean dynamic;

    ABIType(String canonicalType, Class<J> clazz, boolean dynamic) {
        this.canonicalType = canonicalType; // .intern() to save memory and allow == comparison?
        this.clazz = clazz;
        this.dynamic = dynamic;
    }

    public final String getCanonicalType() {
        return canonicalType;
    }

    public final Class<J> clazz() {
        return clazz;
    }

    public final boolean isDynamic() {
        return dynamic;
    }

    public int getFlags() {
        return FLAGS_UNSET;
    }

    abstract Class<?> arrayClass();

    /**
     * Returns an integer code specific to this instance's class, which is a subclass of {@link ABIType}.
     *
     * @return the code
     */
    public abstract int typeCode();

    abstract int headLength();

    int dynamicByteLength(J value) {
        throw new UnsupportedOperationException();
    }

    /**
     * @param value the value to measure
     * @return the length in bytes of the value when encoded
     */
    abstract int byteLength(J value);

    abstract int byteLengthPacked(J value);

    /**
     * Checks whether the given object is a valid argument for this {@link ABIType}. Requires an instance of type J.
     *
     * @param value an object of type J
     * @return the byte length of the ABI encoding of {@code value}
     */
    public abstract int validate(J value);

    final void validateClass(J value) {
        if(!clazz.isInstance(value)) {
            if(value == null) {
                throw new NullPointerException();
            }
            throw mismatchErr("class",
                    value.getClass().getName(), clazz.getName(),
                    friendlyClassName(clazz, -1), friendlyClassName(value.getClass(), -1));
        }
    }

    final IllegalArgumentException mismatchErr(String prefix, String a, String e, String r, String f) {
        return new IllegalArgumentException(
                prefix + " mismatch: " + a + " != " + e + " ("
                        + canonicalType + " requires " + r + " but found " + f + ")"
        );
    }

    public final int measureEncodedLength(J value) {
        return validate(value);
    }

    public final ByteBuffer encode(J value) {
        ByteBuffer dest = ByteBuffer.allocate(validate(value));
        encodeTail(value, dest);
        dest.flip();
        return dest;
    }

    public final void encode(J value, ByteBuffer dest) {
        validate(value);
        encodeTail(value, dest);
    }

    abstract void encodeTail(J value, ByteBuffer dest);

    /**
     * Returns the non-standard packed encoding of {@code value}.
     *
     * @param value the argument to be encoded
     * @return the encoding
     */
    public final ByteBuffer encodePacked(J value) {
        validate(value);
        ByteBuffer dest = ByteBuffer.allocate(byteLengthPacked(value));
        encodePackedUnchecked(value, dest);
        return dest;
    }

    /**
     * Puts into the given {@link ByteBuffer} at its current position the non-standard packed encoding of {@code value}.
     *
     * @param value the argument to be encoded
     * @param dest   the destination buffer
     */
    public final void encodePacked(J value, ByteBuffer dest) {
        validate(value);
        encodePackedUnchecked(value, dest);
    }

    abstract void encodePackedUnchecked(J value, ByteBuffer dest);

    public final J decode(byte[] array) {
        return decode(array, 0, array.length);
    }

    J decode(byte[] buffer, int offset, int len) {
        ByteBuffer bb = ByteBuffer.wrap(buffer, offset, len);
        J decoded = decode(bb);
        final int remaining = bb.remaining();
        if(remaining == 0) {
            return decoded;
        }
        throw new IllegalArgumentException("unconsumed bytes: " + remaining + " remaining");
    }

    public final J decode(ByteBuffer buffer) {
        return decode(buffer, newUnitBuffer());
    }

    /**
     * Decodes the data at the buffer's current position according to this {@link ABIType}.
     *
     * @param buffer     the buffer containing the encoded data
     * @param unitBuffer a buffer of length {@link UnitType#UNIT_LENGTH_BYTES} in which to store intermediate values
     * @return the decoded value
     * @throws IllegalArgumentException if the data is malformed
     */
    abstract J decode(ByteBuffer buffer, byte[] unitBuffer);

    public final J decodePacked(byte[] buffer) {
        return PackedDecoder.decode(
                    new TupleType('(' + this.canonicalType + ')', dynamic, new ABIType[] { this }, null, null, this.getFlags()),
                    buffer
                ).get(0);
    }

    static byte[] newUnitBuffer() {
        return new byte[UNIT_LENGTH_BYTES];
    }

    @Override
    public final int hashCode() {
        return 31 * canonicalType.hashCode() + getFlags();
    }

    @Override
    public final boolean equals(Object o) {
        if (o == this) return true;
        if (o instanceof ABIType) {
            final ABIType<?> other = (ABIType<?>) o;
            return other.canonicalType.equals(this.canonicalType) && other.getFlags() == this.getFlags();
        }
        return false;
    }

    @Override
    public final String toString() {
        return canonicalType;
    }

    static void insertIntUnsigned(int val, ByteBuffer dest) {
        insert00Padding(UNIT_LENGTH_BYTES - Integer.BYTES, dest);
        dest.putInt(val);
    }

    static void insertInt(long val, ByteBuffer dest) {
        insertPadding(UNIT_LENGTH_BYTES - Long.BYTES, val < 0, dest);
        dest.putLong(val);
    }

    static void insertInt(BigInteger signed, int paddedLen, ByteBuffer dest) {
        final byte[] arr = signed.toByteArray();
        if(arr.length <= paddedLen) {
            insertPadding(paddedLen - arr.length, signed.signum() < 0, dest);
            dest.put(arr, 0, arr.length);
        } else {
            dest.put(arr, 1, paddedLen);
        }
    }

    private static void insertPadding(int n, boolean negativeOnes, ByteBuffer dest) {
        if(negativeOnes) {
            insertFFPadding(n, dest);
        } else {
            insert00Padding(n, dest);
        }
    }

    static void insert00Padding(int n, ByteBuffer dest) {
        dest.put(CACHED_ZERO_PADDING, 0, n);
    }

    static void insertFFPadding(int n, ByteBuffer dest) {
        dest.put(CACHED_NEG1_PADDING, 0, n);
    }

    private static final int LABEL_LEN = 6;
    private static final int LABEL_PADDED_LEN = LABEL_LEN + 3;

    public static String format(byte[] abi) {
        return format(abi, (int row) -> {
            String unpadded = Integer.toHexString(row * UNIT_LENGTH_BYTES);
            return pad(LABEL_LEN - unpadded.length(), unpadded);
        });
    }

    public static String format(byte[] abi, IntFunction<String> labeler) {
        Integers.checkIsMultiple(abi.length, UNIT_LENGTH_BYTES);
        return finishFormat(abi, 0, abi.length, labeler, new StringBuilder());
    }

    static String finishFormat(byte[] buffer, int offset, int end, IntFunction<String> labeler, StringBuilder sb) {
        int row = 0;
        while(offset < end) {
            if(offset > 0) {
                sb.append('\n');
            }
            sb.append(labeler.apply(row++))
                    .append(Strings.encode(buffer, offset, UNIT_LENGTH_BYTES, Strings.HEX));
            offset += UNIT_LENGTH_BYTES;
        }
        return sb.toString();
    }

    static String pad(int leftPadding, String unpadded) {
        StringBuilder sb = new StringBuilder();
        pad(sb, leftPadding);
        sb.append(unpadded);
        pad(sb, LABEL_PADDED_LEN - sb.length());
        return sb.toString();
    }

    private static void pad(StringBuilder sb, int n) {
        for (int i = 0; i < n; i++) {
            sb.append(' ');
        }
    }

    static String friendlyClassName(Class<?> clazz, int arrayLen) {
        final String className = clazz.getName();
        final int split = className.lastIndexOf('[') + 1;
        final boolean hasArraySuffix = split > 0;
        final StringBuilder sb = new StringBuilder();
        final String base = hasArraySuffix ? className.substring(split) : className;
        switch (base) {
            case "B": sb.append("byte"); break;
            case "S": sb.append("short"); break;
            case "I": sb.append("int"); break;
            case "J": sb.append("long"); break;
            case "F": sb.append("float"); break;
            case "D": sb.append("double"); break;
            case "C": sb.append("char"); break;
            case "Z": sb.append("boolean"); break;
            default: {
                int lastDotIndex = base.lastIndexOf('.');
                if(lastDotIndex != -1) {
                    sb.append(base, lastDotIndex + 1, base.length() - (base.charAt(0) == 'L' ? 1 : 0));
                }
            }
        }
        if(hasArraySuffix) {
            int i = 0;
            if(arrayLen >= 0) {
                sb.append('[').append(arrayLen).append(']');
                i++;
            }
            while (i++ < split) {
                sb.append("[]");
            }
        }
        return sb.toString();
    }
}
