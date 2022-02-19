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

import java.math.BigInteger;
import java.nio.ByteBuffer;

/** Represents an integer type such as uint64 or int256. */
public final class BigIntegerType extends UnitType<BigInteger> {

    BigIntegerType(String canonicalType, int bitLength, boolean unsigned, String name) {
        super(canonicalType, BigInteger.class, bitLength, unsigned, name);
    }

    @Override
    Class<?> arrayClass() {
        return BigInteger[].class;
    }

    @Override
    public int typeCode() {
        return TYPE_CODE_BIG_INTEGER;
    }

    @Override
    public int validate(BigInteger value) {
        return validateBigInt(value);
    }

    @Override
    void encodeTail(Object value, ByteBuffer dest) {
        Encoding.insertInt((BigInteger) value, UNIT_LENGTH_BYTES, dest);
    }

    @Override
    void encodePackedUnchecked(BigInteger value, ByteBuffer dest) {
        Encoding.insertInt(value, byteLengthPacked(null), dest);
    }

    @Override
    BigInteger decode(ByteBuffer bb, byte[] unitBuffer) {
        return decodeValid(bb, unitBuffer);
    }

    @Override
    public BigInteger parseArgument(String s) {
        BigInteger bigInt = new BigInteger(s);
        validate(bigInt);
        return bigInt;
    }
}
