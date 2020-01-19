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

import com.esaulpaugh.headlong.TestUtils;
import com.esaulpaugh.headlong.abi.util.BizarroIntegers;
import com.esaulpaugh.headlong.util.Strings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PackedEncoderTest {

    @Test
    public void multipleDynamic() throws Throwable {
        TupleType tupleType = TupleType.parse("(int120[],(int96,ufixed256x47)[])");
        Tuple test = Tuple.of(new BigInteger[] { BigInteger.TEN }, new Tuple[] { Tuple.of(BigInteger.TEN, new BigDecimal(BigInteger.TEN, 47)) });
        ByteBuffer bb = tupleType.encodePacked(test);
        TestUtils.assertThrown(IllegalArgumentException.class, "multiple dynamic elements", () -> PackedDecoder.decode(tupleType, bb.array()));

        TupleType _tt = TupleType.parse("(int144[][1])");
        Tuple _test = Tuple.of((Object) new BigInteger[][] { new BigInteger[] { } });
        _tt.validate(_test);
        ByteBuffer _bb = _tt.encodePacked(_test);
        TestUtils.assertThrown(IllegalArgumentException.class, "array of dynamic elements", () -> PackedDecoder.decode(_tt, _bb.array()));
    }

    @Test
    public void testTupleArray() throws ABIException {
        TupleType tupleType = TupleType.parse("((bool)[])");

        Tuple test = Tuple.of(((Object) new Tuple[] { Tuple.of(true), Tuple.of(false), Tuple.of(true) }));

        ByteBuffer bb = tupleType.encodePacked(test);

        assertEquals("010001", Function.hexOf(bb));

        Tuple decoded = PackedDecoder.decode(tupleType, bb.array());

        assertEquals(test, decoded);
    }

    @Test
    public void testEmptyTupleArray() throws Throwable {
        TupleType tupleType = TupleType.parse("(()[])");

        Tuple test = Tuple.of(((Object) new Tuple[0]));

        ByteBuffer bb = tupleType.encodePacked(test);

        assertEquals("", Function.hexOf(bb));

        TestUtils.assertThrown(IllegalArgumentException.class, "can't decode dynamic number of zero-length elements", () -> PackedDecoder.decode(tupleType, bb.array()));
    }

    @Test
    public void testHard() throws ABIException {
        TupleType tupleType = TupleType.parse("((bytes,(uint8[2][2])))");

        Tuple test = Tuple.of(Tuple.of(new byte[0], Tuple.of((Object) new int[][] { new int[] {1,2}, new int[] {3,4} })));

        ByteBuffer bb = tupleType.encodePacked(test);

        assertEquals("01020304", Function.hexOf(bb));

        byte[] packed = Strings.decode("01020304");

        ByteBuffer buf = ByteBuffer.allocate(100);
        buf.put(new byte[17]);
        buf.put(packed);
        buf.put((byte) 0xff);

        Tuple decoded = PackedDecoder.decode(tupleType, buf.array(), 17, 17 + packed.length);

        assertEquals(test, decoded);
    }

    @Test
    public void testStaticTupleInsideDynamic() throws ABIException {
        TupleType tupleType = TupleType.parse("((bytes1),bytes)");

        Tuple test = Tuple.of(new Tuple((Object) new byte[] { -1 }), new byte[] { -15, -15 });

        ByteBuffer bb = tupleType.encodePacked(test);

        assertEquals("fff1f1", Function.hexOf(bb));

        Tuple decoded = PackedDecoder.decode(tupleType, Strings.decode("fff1f1"));

        assertEquals(test, decoded);
    }

    @Test
    public void testPacked() throws ABIException {
        TupleType tupleType = TupleType.parse("(int16,bytes1,uint16,string)");

        Tuple test = new Tuple(-1, new byte[] { 0x42 }, 0x03, "Hello, world!");

        int packedLen = tupleType.byteLengthPacked(test);

        assertEquals(Strings.decode("ffff42000348656c6c6f2c20776f726c6421").length, packedLen);

        ByteBuffer bb = ByteBuffer.allocate(packedLen);

        tupleType.encodePacked(test, bb);

        assertEquals(packedLen, bb.position());

        System.out.println(Function.hexOf(bb));

        assertEquals("ffff42000348656c6c6f2c20776f726c6421", Function.hexOf(bb));

        // ---------------------------

        Function function = new Function(tupleType.canonicalType);

        String hex = Function.hexOf(function.getParamTypes().encode(test));

        System.out.println(hex);

        byte[] abi = Strings.decode("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff420000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000000d48656c6c6f2c20776f726c642100000000000000000000000000000000000000");

        byte[] call = new byte[Function.SELECTOR_LEN + abi.length];

        System.arraycopy(function.selector(), 0, call, 0, Function.SELECTOR_LEN);
        System.arraycopy(abi, 0, call, Function.SELECTOR_LEN, abi.length);

        Tuple args = function.decodeCall(call);

        TupleType tt = TupleType.parse(tupleType.canonicalType);

        ByteBuffer dest2 = tt.encodePacked(args);

        System.out.println(Function.hexOf(dest2));

        assertEquals("ffff42000348656c6c6f2c20776f726c6421", Function.hexOf(dest2));

    }

    @Test
    public void testTest() throws ABIException {
        TupleType tupleType = TupleType.parse("(int24,bool,bool)");

        Tuple values = new Tuple(-2, true, false);

        ByteBuffer bb = tupleType.encodePacked(values);
        assertEquals(bb.capacity(), bb.position());

        System.out.println(Function.hexOf(bb));

        assertEquals("fffffe0100", Function.hexOf(bb));

        Tuple decoded = PackedDecoder.decode(tupleType, bb.array());

        assertEquals(values, decoded);
    }

    @Test
    public void testDecodeA() throws ABIException {
        TupleType tupleType = TupleType.parse("(int16[2],int24[3],bytes,uint32[3],bool[3],uint64,int72)");

        Tuple test = new Tuple(new int[] { 3, 5 }, new int[] { 7, 8, 9 }, new byte[0], new int[] { 9, 0, -1 }, new boolean[] { true, false, true }, BigInteger.valueOf(6L), BigInteger.valueOf(-1L));

        ByteBuffer bb = tupleType.encodePacked(test);
        assertEquals(bb.capacity(), bb.position());

        System.out.println(Function.hexOf(bb));

        assertEquals("000300050000070000080000090000000900000000ffffffff0100010000000000000006ffffffffffffffffff", Function.hexOf(bb));

        Tuple decoded = PackedDecoder.decode(tupleType, bb.array());

        assertEquals(test, decoded);
    }

    @Test
    public void testDecodeB() throws ABIException {
        TupleType tupleType = TupleType.parse("(uint64[],int)");

        Tuple values = new Tuple(new long[] { 1L, 2L, 3L, 4L }, BigInteger.ONE);

        ByteBuffer bb = tupleType.encodePacked(values);
        byte[] packedArray = bb.array();
        assertEquals(packedArray.length, bb.position());

        System.out.println(Function.hexOf(bb));

        assertEquals("00000000000000010000000000000002000000000000000300000000000000040000000000000000000000000000000000000000000000000000000000000001", Function.hexOf(bb));

        Tuple decoded = PackedDecoder.decode(tupleType, packedArray);

        assertEquals(values, decoded);
    }

    @Test
    public void testDecodeC() throws ABIException {
        TupleType tupleType = TupleType.parse("(bool,bool[],bool[2])");

        Tuple values = new Tuple(true, new boolean[] { true, true, true },  new boolean[] { true, false });

        ByteBuffer bb = tupleType.encodePacked(values);
        assertEquals(bb.capacity(), bb.position());

        System.out.println(Function.hexOf(bb));

        assertEquals("010101010100", Function.hexOf(bb));

        Tuple decoded = PackedDecoder.decode(tupleType, bb.array());

        assertEquals(values, decoded);
    }

    @Test
    public void testSignExtendInt() {
        int expected = BizarroIntegers.getInt(Strings.decode("8FFFFF"), 0, 3);
        int result = PackedDecoder.getPackedInt(Strings.decode("8FFFFF"), 0, 3);
        assertTrue(result < 0);
        assertEquals(expected, result);
    }

    @Test
    public void testSignExtendLong() {
        long expectedL = BizarroIntegers.getLong(Strings.decode("8FFFFFFFFF"), 0, 5);
        long resultL = PackedDecoder.getPackedLong(Strings.decode("8FFFFFFFFF"), 0, 5);
        assertTrue(resultL < 0);
        assertEquals(expectedL, resultL);
    }
}
