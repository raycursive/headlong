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
package com.esaulpaugh.headlong.rlp;

import com.esaulpaugh.headlong.util.Integers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/** Extends {@link RLPItem}. Implements {@link Iterable}. Created by Evo on 1/19/2017. */
public final class RLPList extends RLPItem implements Iterable<RLPItem> {

    RLPList(byte[] buffer, int index, int dataIndex, int dataLength, int endIndex) {
        super(buffer, index, dataIndex, dataLength, endIndex);
    }

    @Override
    public boolean isString() {
        return false;
    }

    @Override
    public boolean isList() {
        return true;
    }

    @Override
    public RLPString asRLPString() {
        throw new ClassCastException("not an " + RLPString.class.getSimpleName());
    }

    @Override
    public RLPList asRLPList() {
        return this;
    }

    /** @see RLPItem#duplicate() */
    @Override
    public RLPList duplicate() {
        return new RLPList(buffer, index, dataIndex, dataLength, endIndex);
    }

    /**
     * @param elements pre-encoded top-level elements of the list
     */
    static RLPList withElements(Iterable<RLPItem> elements) {
        int dataLen = 0;
        for (RLPItem e : elements) {
            dataLen += e.encodingLength();
        }
        return RLPDecoder.RLP_STRICT.wrapList(
                dataLen < DataType.MIN_LONG_DATA_LEN
                        ? encodeListShort(dataLen, elements)
                        : encodeListLong(dataLen, elements)
        );
    }

    private static byte[] encodeListShort(final int dataLen, final Iterable<RLPItem> elements) {
        byte[] dest = new byte[1 + dataLen];
        dest[0] = (byte) (DataType.LIST_SHORT_OFFSET + dataLen);
        copyElements(elements, dest, 1);
        return dest;
    }

    private static byte[] encodeListLong(final int dataLen, final Iterable<RLPItem> elements) {
        final int lengthOfLength = Integers.len(dataLen);
        final int prefixLen = 1 + lengthOfLength;
        byte[] dest = new byte[prefixLen + dataLen];
        dest[0] = (byte) (DataType.LIST_LONG_OFFSET + lengthOfLength);
        Integers.putLong(dataLen, dest, 1);
        copyElements(elements, dest, prefixLen);
        return dest;
    }

    private static void copyElements(Iterable<RLPItem> elements, byte[] dest, int destIndex) {
        for (RLPItem e : elements) {
            destIndex = e.export(dest, destIndex);
        }
    }

    public List<RLPItem> elements() {
        return elements(RLPDecoder.RLP_STRICT);
    }

    public List<RLPItem> elements(RLPDecoder decoder) {
        ArrayList<RLPItem> arrayList = new ArrayList<>();
        elements(decoder, arrayList);
        return arrayList;
    }

    public void elements(RLPDecoder decoder, Collection<RLPItem> collection) {
        int i = dataIndex;
        while (i < endIndex) {
            RLPItem item = decoder.wrap(buffer, i, endIndex);
            collection.add(item);
            i = item.endIndex;
        }
    }

    public Iterator<RLPItem> iterator(RLPDecoder decoder) {
        return new RLPListIterator(decoder, this);
    }

    @Override
    public Iterator<RLPItem> iterator() {
        return iterator(RLPDecoder.RLP_STRICT);
    }

    private static final class RLPListIterator implements Iterator<RLPItem> {

        private final RLPDecoder decoder;
        private final byte[] buffer;
        private int idx;
        private final int endIndex;

        RLPListIterator(RLPDecoder decoder, RLPList rlpList) {
            this.decoder = decoder;
            this.buffer = rlpList.buffer;
            this.idx = rlpList.dataIndex;
            this.endIndex = rlpList.endIndex;
        }

        @Override
        public boolean hasNext() {
            return idx < endIndex;
        }

        @Override
        public RLPItem next() {
            if (hasNext()) {
                RLPItem next = decoder.wrap(buffer, idx, endIndex);
                idx = next.endIndex;
                return next;
            }
            throw new NoSuchElementException();
        }
    }
}
