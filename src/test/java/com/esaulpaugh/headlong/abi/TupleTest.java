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
import com.esaulpaugh.headlong.util.Strings;
import com.joemelsha.crypto.hash.Keccak;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static com.esaulpaugh.headlong.TestUtils.assertThrown;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TupleTest {

    @Disabled("meta test")
    @Test
    public void metaTest1() {
        Random r = ThreadLocalRandom.current();

        int bits = 24;

        final int pow = (int) Math.pow(2.0, bits);
        final int powMinus1 = pow - 1;
        System.out.println(Long.toHexString(powMinus1));

        System.out.println(pow);

        boolean[] bools = new boolean[pow];

        BigIntegerType type = new BigIntegerType("int" + bits, bits, false);
//        BooleanType type = new BooleanType();

        for (int i = 0; i < 1_579_919_999; i++) {
            bools[MonteCarloTestCase.generateBigInteger(r, type).intValue() & powMinus1] = true;
//            bools[(int) MonteCarloTestCase.generateLong(r, type) & powMinus1] = true;
        }

        int count = 0;
        for (int i = 0; i < pow; i++) {
            if(!bools[i]) {
                count++;
                System.err.println(i);
            }
        }

        System.out.println("missed " + count);
        System.out.println(pow - count + " / " + pow + " " + (1 - ((double) count / pow)));
    }

    @Test
    public void testTuple() {
        final Tuple emptyA = new Tuple();
        final Tuple emptyB = Tuple.of();

        assertEquals(Tuple.EMPTY, emptyA);
        assertEquals(Tuple.EMPTY, emptyB);

        assertTrue(Tuple.EMPTY.isEmpty());
        assertTrue(emptyA.isEmpty());
        assertTrue(emptyB.isEmpty());

        assertFalse(Tuple.singleton(0).isEmpty());
        assertFalse(Tuple.singleton(false).isEmpty());
        assertFalse(Tuple.singleton(new Object()).isEmpty());
    }

    private static final Object[] OBJECTS = new Object[] {
            new byte[0],
            new int[0],
            new short[0],
            new long[0],
            new boolean[0],
            new Throwable() {},
            new BigInteger[0],
            new BigDecimal[0],
            true,
            5,
            9L,
            "aha",
            '\0',
            Tuple.EMPTY,
            0.1f,
            1.9d
    };

    @Test
    public void testTypeSafety() throws Throwable {

        final int maxTupleLen = 3;
        final MonteCarloTestCase.Limits limits = new MonteCarloTestCase.Limits(maxTupleLen, 3, 3, 3);
        final Random r = TestUtils.seededRandom();
        final Keccak k = new Keccak(256);
        final Object defaultObj = new Object();

        long seed = r.nextLong();
        for (int idx = 0; idx < maxTupleLen; idx++) {
            for (int i = 0; i < 25; i++, seed++) {
                r.setSeed(seed);

                final MonteCarloTestCase testCase = new MonteCarloTestCase(seed, limits, r, k);
                final Object[] elements = testCase.argsTuple.elements;
                if (idx < elements.length) {
                    Object replacement = OBJECTS[r.nextInt(OBJECTS.length)];
                    elements[idx] = elements[idx].getClass() != replacement.getClass()
                            ? replacement
                            : defaultObj;
                    try {
                        assertThrown(IllegalArgumentException.class, " but found ", () -> testCase.function.encodeCall(Tuple.of(elements)));
                    } catch (AssertionError ae) {
                        System.err.println("seed = " + seed);
                        ae.printStackTrace();
                        throw ae;
                    }
                }
            }
        }
    }

    @Test
    public void fuzzNulls() throws Throwable {
        final MonteCarloTestCase.Limits limits = new MonteCarloTestCase.Limits(3, 3, 3, 3);
        final Random r = TestUtils.seededRandom();
        final Keccak k = new Keccak(256);
        for (int i = 0; i < 1000; i++) {
            MonteCarloTestCase mctc = new MonteCarloTestCase(r.nextLong(), limits, r, k);
            Tuple args = mctc.argsTuple;
            if(args.elements.length > 0) {
                int idx = r.nextInt(args.elements.length);
                replace(args.elements, idx);
                assertThrown(IllegalArgumentException.class, "null", () -> mctc.function.encodeCall(args));
            }
        }
    }

    private static void replace(Object[] parent, int index) {
        Object element = parent[index];
        if(element instanceof Object[]) {
            Object[] eArr = (Object[]) element;
            if(eArr.length > 0) {
                Object inner = parent[index];
                if(inner instanceof Object[]) {
                    Object[] innerArr = (Object[]) inner;
                    if(innerArr.length > 0) {
                        innerArr[innerArr.length - 1] = null;
                        return;
                    }
                }
                eArr[0] = null;
                return;
            }
        }
        parent[index] = null;
    }

    @Test
    public void testSubtubleType() throws Throwable {
        TupleType tt = TupleType.parse("(bytes3,uint16[],string)");

        assertEquals(TupleType.EMPTY, tt.select(false, false, false));
        assertEquals(TupleType.of("string"), tt.select(false, false, true));
        assertEquals(TupleType.of("uint16[]"), tt.select(false, true, false));
        assertEquals(TupleType.of("uint16[]", "string"), tt.select(false, true, true));
        assertEquals(TupleType.of("bytes3"), tt.select(true, false, false));
        assertEquals(TupleType.of("bytes3", "string"), tt.select(true, false, true));
        assertEquals(TupleType.of("bytes3", "uint16[]"), tt.select(true, true, false));
        assertEquals(TupleType.of("bytes3", "uint16[]", "string"), tt.select(true, true, true));

        assertThrown(IllegalArgumentException.class, "manifest.length != size()", () -> tt.select(true, true));
        assertThrown(IllegalArgumentException.class, "manifest.length != size()", () -> tt.select(false, false, false, false));

        assertEquals(TupleType.of("bytes3", "uint16[]", "string"), tt.exclude(false, false, false));
        assertEquals(TupleType.of("bytes3", "uint16[]"), tt.exclude(false, false, true));
        assertEquals(TupleType.of("bytes3", "string"), tt.exclude(false, true, false));
        assertEquals(TupleType.of("bytes3"), tt.exclude(false, true, true));
        assertEquals(TupleType.of("uint16[]", "string"), tt.exclude(true, false, false));
        assertEquals(TupleType.of("uint16[]"), tt.exclude(true, false, true));
        assertEquals(TupleType.of("string"), tt.exclude(true, true, false));
        assertEquals(TupleType.EMPTY, tt.exclude(true, true, true));

        assertThrown(IllegalArgumentException.class, "manifest.length != size()", () -> tt.select(new boolean[0]));
        assertThrown(IllegalArgumentException.class, "manifest.length != size()", () -> tt.select(new boolean[5]));
    }

    @Test
    public void testNameOverwrites() throws Throwable {
        testNameOverwrite("(bool)", "moo", "jumbo");
        testNameOverwrite("(())", "zZz", "Jumb0");

        assertThrown(IllegalArgumentException.class, "expected 2 element names but found 0",
                () -> TypeFactory.createTupleTypeWithNames("(bool,string)"));

        assertThrown(IllegalArgumentException.class, "expected 3 element names but found 2",
                () -> TypeFactory.createTupleTypeWithNames("(bool,string,int)", "a", "b"));

        assertThrown(IllegalArgumentException.class, "expected 2 element names but found 4",
                () -> TypeFactory.createTupleTypeWithNames("(bool,string)", new String[4]));

        TupleType tt = TypeFactory.createTupleTypeWithNames("(bool,string)", "a", "b");
        assertThrown(IllegalArgumentException.class, "index out of bounds: -1", () -> tt.getElementName(-1));
        assertEquals("a", tt.getElementName(0));
        assertEquals("b", tt.getElementName(1));
        assertThrown(IllegalArgumentException.class, "index out of bounds: 2", () -> tt.getElementName(2));
    }

    private static void testNameOverwrite(String typeStr, String aName, String cName) {
        assertNotEquals(aName, cName);

        final TupleType a = TypeFactory.createTupleTypeWithNames(typeStr, aName);
        assertEquals(aName, a.getElementName(0));

        final TupleType b = TypeFactory.create(typeStr);
        assertEquals(aName, a.getElementName(0));
        assertNull(b.getElementName(0));

        final TupleType c = TypeFactory.createTupleTypeWithNames(typeStr, cName);
        assertEquals(aName, a.getElementName(0));
        assertNull(b.getElementName(0));
        assertEquals(cName, c.getElementName(0));

        assertNotEquals(aName, cName);

        final TupleType x = wrap(new String[] { aName }, a.get(0));
        assertEquals(aName, x.getElementName(0));

        final TupleType y = wrap(null, b.get(0));
        assertEquals(aName, x.getElementName(0));
        assertNull(y.getElementName(0));

        final TupleType z = wrap(new String[] { cName }, c.get(0));
        assertEquals(aName, x.getElementName(0));
        assertNull(y.getElementName(0));
        assertEquals(cName, z.getElementName(0));
    }

    static TupleType wrap(String[] elementNames, ABIType<?>... elements) {
        final StringBuilder canonicalBuilder = new StringBuilder("(");
        boolean dynamic = false;
        for (ABIType<?> e : elements) {
            canonicalBuilder.append(e.canonicalType).append(',');
            dynamic |= e.dynamic;
        }
        return new TupleType(TupleType.completeTupleTypeString(canonicalBuilder), dynamic, elementNames, elements);
    }

    @Test
    public void testTupleImmutability() throws Throwable {
        Object[] args = new Object[] { "a", "b", "c" };
        Tuple t = Tuple.of((Object[]) args); // shallow copy

        args[1] = 'x';
        assertEquals("a", t.get(0));
        assertEquals("b", t.get(1));
        assertEquals("c", t.get(2));

        args = new Object[] { "a", "b", "c" };
        t = new Tuple((Object[]) args); // no shallow copy

        args[1] = 'x';
        assertEquals("a", t.get(0));
        assertEquals(Character.valueOf('x'), t.get(1));
        assertEquals("c", t.get(2));

//        List<Object> list = t.toList();
//        final int size = list.size();
//        assertEquals(args.length, size);
//        assertEquals(t.size(), size);
//        assertEquals(t.elements.length, size);
//
//        assertThrown(UnsupportedOperationException.class, () -> list.set(0, "d"));
//        assertThrown(UnsupportedOperationException.class, () -> list.replaceAll(s -> ""));
//        assertThrown(UnsupportedOperationException.class, () -> Collections.fill(list, ""));

        testRemove(t.iterator());
//        testRemove(list.iterator());
    }

    @Test
    public void testTupleTypeImmutability() throws Throwable {
        testRemove(TupleType.parse("(bool,int,string)").iterator());
    }

    private static void testRemove(Iterator<?> iter) throws Throwable {
        assertTrue(iter.hasNext());
        iter.next();
        assertThrown(UnsupportedOperationException.class, iter::remove);
    }

    @Test
    public void testDecodeIndex0() {
        TupleType tt = TupleType.parse("(bool,(bool,int24[2],(bool,bool)[2])[1],string)");
        Tuple args = Tuple.of(true, new Tuple[] { Tuple.of(true, new int[] { 1, 2 }, new Tuple[] { Tuple.of(true, false), Tuple.of(true, false) }) }, "ya");
        ByteBuffer bb = tt.encode(args);
        System.out.println(Strings.encode(bb));
        String ya = tt.decode(bb, 2);
        assertEquals("ya", ya);
    }

    @Test
    public void testDecodeIndex1() {
        TupleType tt = TupleType.parse("(bool,bool[3][2],string[][])");
        Tuple args = Tuple.of(true, new boolean[][] { new boolean[] { true, false, true }, new boolean[] { false, false, true } }, new String[][] { new String[] { "wooo", "moo" } });
        ByteBuffer bb = tt.encode(args);
        System.out.println(Strings.encode(bb));
        String[][] s = tt.decode(bb, 2);
        assertTrue(Objects.deepEquals(new String[][] { new String[] { "wooo", "moo" } }, s));
    }

    @Test
    public void testDecodeIndex2() {
        TupleType tt = TupleType.parse("(bool,uint16,address,int64,uint64,address,string[][])");
        Tuple args = Tuple.of(
                true,
                90,
                Address.wrap("0x0000000000000000000000000000000000000000"),
                100L,
                BigInteger.valueOf(110L),
                Address.wrap("0x0000110000111100001111110000111111110000"),
                new String[][] { new String[] { "yabba", "dabba", "doo" }, new String[] { "" } }
        );
        final ByteBuffer bb = tt.encode(args);
        boolean bool = tt.decode(bb, 0);
        assertTrue(bool);
        int uint16 = tt.decode(bb, 1);
        assertEquals(90, uint16);
        Address address = tt.decode(bb, 2);
        assertEquals(Address.wrap("0x0000000000000000000000000000000000000000"), address);
        long int64 = tt.decode(bb, 3);
        assertEquals(100L, int64);
        BigInteger uint64 = tt.decode(bb, 4);
        assertEquals(new BigInteger("110"), uint64);
        Address address2 = tt.decode(bb, 5);
        assertEquals(Address.wrap("0x0000110000111100001111110000111111110000"), address2);
        String[][] arrs = tt.decode(bb, 6);
        assertTrue(Objects.deepEquals(new String[][] { new String[] { "yabba", "dabba", "doo" }, new String[] { "" } }, arrs));
    }

    @Test
    public void testSelectExclude() {
        TupleType _uintBool_ = TupleType.parse("(uint,bool)");
        TupleType _uint_ = TupleType.parse("(uint)");
        TupleType _bool_ = TupleType.parse("(bool)");

        assertEquals(TupleType.EMPTY,   _uintBool_.select(false, false));
        assertEquals(_bool_,            _uintBool_.select(false, true));
        assertEquals(_uint_,            _uintBool_.select(true, false));
        assertEquals(_uintBool_,        _uintBool_.select(true, true));

        assertEquals(_uintBool_,        _uintBool_.exclude(false, false));
        assertEquals(_uint_,            _uintBool_.exclude(false, true));
        assertEquals(_bool_,            _uintBool_.exclude(true, false));
        assertEquals(TupleType.EMPTY,   _uintBool_.exclude(true, true));
        
        TupleType tt2 = TupleType.parse("((int,bool))");
        assertEquals(tt2, tt2.select(true));
        assertEquals(TupleType.EMPTY, tt2.exclude(true));

        assertEquals(TupleType.EMPTY, TupleType.EMPTY.select());
        assertEquals(TupleType.EMPTY, TupleType.EMPTY.exclude());

        TupleType clone0 = _uintBool_.select(true, true);
        assertSame(_uintBool_.get(0), clone0.get(0));
        assertSame(_uintBool_.get(1), clone0.get(1));

        TupleType clone1 = _uintBool_.exclude(false, false);
        assertSame(_uintBool_.get(0), clone1.get(0));
        assertSame(_uintBool_.get(1), clone1.get(1));

        assertSame(_uintBool_.get(0), _uintBool_.select(true, false).get(0));
        assertSame(_uintBool_.get(1), _uintBool_.select(false, true).get(0));
    }

    @Test
    public void testGetElement() {
        TupleType tt = TupleType.parse("(bytes8,decimal)");
        ArrayType<ByteType, byte[]> at = tt.get(0);
        assertEquals(8, at.getLength());
        BigDecimalType decimal = tt.get(1);
        assertEquals("fixed168x10", decimal.getCanonicalType());

        Tuple t = Tuple.of("iii");
        String iii = t.get(0);
        assertEquals("iii", iii);

        TupleType outer = TupleType.parse("((address,int256))");
        TupleType inner = outer.get(0);
        assertEquals(TupleType.parse("(address,int)"), inner);
    }

    @Test
    public void testTupleLengthMismatch() throws Throwable {
        TupleType tt = TupleType.parse("(bool)");
        assertThrown(IllegalArgumentException.class, "tuple length mismatch: actual != expected: 0 != 1", () -> tt.validate(Tuple.EMPTY));
        assertThrown(IllegalArgumentException.class, "tuple length mismatch: actual != expected: 2 != 1", () -> tt.validate(Tuple.of("", "")));
    }

    @Test
    public void testParseBadFixed() throws Throwable {
        assertThrown(IllegalArgumentException.class, "unrecognized type: \"fixed45\"", () -> TypeFactory.create("fixed45"));
    }

    @Test
    public void testLengthLimit() throws Throwable {
        final byte[] typeBytes = new byte[2610];
        final int midpoint = typeBytes.length / 2;
        int i = 0;
        while (i < midpoint) {
            typeBytes[i++] = '(';
        }
        while (i < typeBytes.length) {
            typeBytes[i++] = ')';
        }
        final String ascii = Strings.encode(typeBytes, Strings.ASCII);
        assertThrown(IllegalArgumentException.class, "type length exceeds maximum: 2610 > 2000" , () -> TupleType.parse(ascii));

        final Random r = TestUtils.seededRandom();
        final StringBuilder sb = new StringBuilder(r.nextBoolean() ? "string" : "bool");
        for (int j = 0; j < 1000; j++) {
            if(r.nextBoolean()) {
                sb.append("[]");
            } else {
                sb.append('[').append(r.nextInt(5000)).append(']');
            }
        }
        final String arrType = sb.toString();
        final int len = arrType.length();
        assertThrown(IllegalArgumentException.class, "type length exceeds maximum: " + len + " > 2000" , () -> TypeFactory.create(arrType));
    }
}
