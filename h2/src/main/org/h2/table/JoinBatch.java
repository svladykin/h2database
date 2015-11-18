/*
 * Copyright 2004-2014 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.table;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import org.h2.index.Cursor;
import org.h2.index.IndexCursor;
import org.h2.index.IndexLookupBatch;
import org.h2.index.ViewIndex;
import org.h2.message.DbException;
import org.h2.result.Row;
import org.h2.result.SearchRow;
import org.h2.util.DoneFuture;
import org.h2.util.New;
import org.h2.value.Value;
import org.h2.value.ValueLong;

/**
 * Support for asynchronous batched index lookups on joins.
 * 
 * @see org.h2.index.Index#createLookupBatch(TableFilter)
 * @see IndexLookupBatch
 * @author Sergi Vladykin
 */
public final class JoinBatch {
    private static final Cursor EMPTY_CURSOR = new Cursor() {
        @Override
        public boolean previous() {
            return false;
        }

        @Override
        public boolean next() {
            return false;
        }

        @Override
        public SearchRow getSearchRow() {
            return null;
        }

        @Override
        public Row get() {
            return null;
        }

        @Override
        public String toString() {
            return "EMPTY_CURSOR";
        }
    };

    private static final Future<Cursor> PLACEHOLDER = new DoneFuture<Cursor>(null);

    private ViewIndexLookupBatch lookupBatchWrapper;

    private JoinFilter[] filters;
    private JoinFilter top;

    private boolean started;

    private JoinRow current;
    private boolean found;

    /**
     * This filter joined after this batched join and can be used normally.
     */
    private final TableFilter additionalFilter;

    /**
     * @param filtersCount number of filters participating in this batched join
     * @param additionalFilter table filter after this batched join.
     */
    public JoinBatch(int filtersCount, TableFilter additionalFilter) {
        if (filtersCount > 32) {
            // This is because we store state in a 64 bit field, 2 bits per joined table.
            throw DbException.getUnsupportedException("Too many tables in join (at most 32 supported).");
        }
        filters = new JoinFilter[filtersCount];
        this.additionalFilter = additionalFilter;
    }

    /**
     * Check if the index at the given table filter really supports batching in this query.
     *
     * @param joinFilterId joined table filter id
     * @return {@code true} if index really supports batching in this query
     */
    public boolean isBatchedIndex(int joinFilterId) {
        return filters[joinFilterId].isBatched();
    }

    /**
     * Get the lookup batch for the given table filter.
     *
     * @param joinFilterId joined table filter id
     * @return lookup batch
     */
    public IndexLookupBatch getLookupBatch(int joinFilterId) {
        return filters[joinFilterId].lookupBatch;
    }

    /**
     * Reset state of this batch.
     */
    public void reset() {
        current = null;
        started = false;
        found = false;
        for (JoinFilter jf : filters) {
            jf.reset();
        }
        if (additionalFilter != null) {
            additionalFilter.reset();
        }
    }

    /**
     * @param filter table filter
     * @param lookupBatch lookup batch
     */
    public void register(TableFilter filter, IndexLookupBatch lookupBatch) {
        assert filter != null;
        top = new JoinFilter(lookupBatch, filter, top);
        filters[top.id] = top;
    }

    /**
     * @param filterId table filter id
     * @param column column
     * @return column value for current row
     */
    public Value getValue(int filterId, Column column) {
        Object x = current.row(filterId);
        assert x != null;
        Row row = current.isRow(filterId) ? (Row) x : ((Cursor) x).get();
        int columnId = column.getColumnId();
        if (columnId == -1) {
            return ValueLong.get(row.getKey());
        }
        Value value = row.getValue(column.getColumnId());
        if (value == null) {
            throw DbException.throwInternalError("value is null: " + column + " " + row);
        }
        return value;
    }

    private void start() {
        // initialize current row
        current = new JoinRow(new Object[filters.length]);
        if (lookupBatchWrapper == null) {
            current.updateRow(top.id, top.filter.getIndexCursor(), JoinRow.S_NULL, JoinRow.S_CURSOR);
            // initialize top cursor
            top.filter.getIndexCursor().find(top.filter.getSession(), top.filter.getIndexConditions());
        } else {
            // we are sub-query and joined to upper level query
            // TODO setup
        }
        // we need fake first row because batchedNext always will move to the next row
        JoinRow fake = new JoinRow(null);
        fake.next = current;
        current = fake;
    }

    /**
     * Get next row from the join batch.
     * 
     * @return
     */
    public boolean next() {
        if (!started) {
            start();
            started = true;
        }
        if (additionalFilter == null) {
            if (batchedNext()) {
                assert current.isComplete();
                return true;
            }
            return false;
        }
        for (;;) {
            if (!found) {
                if (!batchedNext()) {
                    return false;
                }
                assert current.isComplete();
                found = true;
                additionalFilter.reset();
            }
            // we call furtherFilter in usual way outside of this batch because it is more effective
            if (additionalFilter.next()) {
                return true;
            }
            found = false;
        }
    }
    
    private static Cursor get(Future<Cursor> f) {
        try {
            return f.get();
        } catch (Exception e) {
            throw DbException.convert(e);
        }
    }
    
    private boolean batchedNext() {
        if (current == null) {
            // after last
            return false;
        }
        // go next
        current = current.next;
        if (current == null) {
            return false;
        }
        current.prev = null;
    
        final int lastJfId = filters.length - 1;
        
        int jfId = lastJfId;
        while (current.row(jfId) == null) {
            // lookup for the first non fetched filter for the current row
            jfId--;
        }
        
        for (;;) {
            fetchCurrent(jfId);
            
            if (!current.isDropped()) {
                // if current was not dropped then it must be fetched successfully
                if (jfId == lastJfId) {
                    // the whole join row is ready to be returned
                    return true;
                }
                JoinFilter join = filters[jfId + 1];
                if (join.isBatchFull()) {
                    // get future cursors for join and go right to fetch them
                    current = join.find(current);
                }
                if (current.row(join.id) != null) {
                    // either find called or outer join with null-row
                    jfId = join.id;
                    continue;
                }
            }
            // we have to go down and fetch next cursors for jfId if it is possible
            if (current.next == null) {
                // either dropped or null-row
                if (current.isDropped()) {
                    current = current.prev;
                    if (current == null) {
                        return false;
                    }
                }
                assert !current.isDropped();
                assert jfId != lastJfId;
                
                jfId = 0;
                while (current.row(jfId) != null) {
                    jfId++;
                }
                // force find on half filled batch (there must be either searchRows 
                // or Cursor.EMPTY set for null-rows)
                current = filters[jfId].find(current);
            } else {
                // here we don't care if the current was dropped
                current = current.next;
                assert !current.isRow(jfId);
                while (current.row(jfId) == null) {
                    assert jfId != top.id;
                    // need to go left and fetch more search rows
                    jfId--;
                    assert !current.isRow(jfId);
                }
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private void fetchCurrent(final int jfId) {
        assert current.prev == null || current.prev.isRow(jfId) : "prev must be already fetched";
        assert jfId == 0 || current.isRow(jfId - 1) : "left must be already fetched";

        assert !current.isRow(jfId) : "double fetching";

        Object x = current.row(jfId);
        assert x != null : "x null";

        final JoinFilter jf = filters[jfId];
        // in case of outer join we don't have any future around empty cursor
        boolean newCursor = x == EMPTY_CURSOR;

        if (!newCursor && current.isFuture(jfId)) {
            // get cursor from a future
            x = get((Future<Cursor>) x);
            current.updateRow(jfId, x, JoinRow.S_FUTURE, JoinRow.S_CURSOR);
            newCursor = true;
        }

        Cursor c = (Cursor) x;
        assert c != null;
        JoinFilter join = jf.join;

        for (;;) {
            if (c == null || !c.next()) {
                if (newCursor && jf.isOuterJoin()) {
                    // replace cursor with null-row
                    current.updateRow(jfId, jf.getNullRow(), JoinRow.S_CURSOR, JoinRow.S_ROW);
                    c = null;
                    newCursor = false;
                } else {
                    // cursor is done, drop it
                    current.drop();
                    return;
                }
            }
            if (!jf.isOk(c == null)) {
                // try another row from the cursor
                continue;
            }
            boolean joinEmpty = false;
            if (join != null && !join.collectSearchRows()) {
                if (join.isOuterJoin()) {
                    joinEmpty = true;
                } else {
                    // join will fail, try next row in the cursor
                    continue;
                }
            }
            if (c != null) {
                current = current.copyBehind(jfId);
                // get current row from cursor
                current.updateRow(jfId, c.get(), JoinRow.S_CURSOR, JoinRow.S_ROW);
            }
            if (joinEmpty) {
                current.updateRow(join.id, EMPTY_CURSOR, JoinRow.S_NULL, JoinRow.S_CURSOR);
            }
            return;
        }
    }

    /**
     * @return Adapter to allow joining to this batch in sub-queries.
     */
    public IndexLookupBatch asViewIndexLookupBatch(ViewIndex viewIndex) {
        if (lookupBatchWrapper == null) {
            lookupBatchWrapper = new ViewIndexLookupBatch(viewIndex);
        }
        return lookupBatchWrapper;
    }

    @Override
    public String toString() {
        return "JoinBatch->\nprev->" + (current == null ? null : current.prev) +
                "\ncurr->" + current + 
                "\nnext->" + (current == null ? null : current.next);
    }

    /**
     * Table filter participating in batched join.
     */
    private static final class JoinFilter {
        private final TableFilter filter;
        private final JoinFilter join;
        private final int id;
        private final boolean fakeBatch;

        private final IndexLookupBatch lookupBatch;

        private JoinFilter(IndexLookupBatch lookupBatch, TableFilter filter, JoinFilter join) {
            this.filter = filter;
            this.id = filter.getJoinFilterId();
            this.join = join;
            fakeBatch = lookupBatch == null;
            this.lookupBatch = fakeBatch ? new FakeLookupBatch(filter) : lookupBatch;
        }

        private boolean isBatched() {
            return !fakeBatch;
        }

        private void reset() {
            lookupBatch.reset();
        }

        private Row getNullRow() {
            return filter.getTable().getNullRow();
        }

        private boolean isOuterJoin() {
            return filter.isJoinOuter();
        }

        private boolean isBatchFull() {
            return lookupBatch.isBatchFull();
        }

        private boolean isOk(boolean ignoreJoinCondition) {
            boolean filterOk = filter.isOk(filter.getFilterCondition());
            boolean joinOk = filter.isOk(filter.getJoinCondition());

            return filterOk && (ignoreJoinCondition || joinOk);
        }

        private boolean collectSearchRows() {
            assert !isBatchFull();
            IndexCursor c = filter.getIndexCursor();
            c.prepare(filter.getSession(), filter.getIndexConditions());
            if (c.isAlwaysFalse()) {
                return false;
            }
            lookupBatch.addSearchRows(c.getStart(), c.getEnd());
            return true;
        }

        private List<Future<Cursor>> find() {
            return lookupBatch.find();
        }

        private JoinRow find(JoinRow current) {
            assert current != null;

            // lookupBatch is allowed to be empty when we have some null-rows and forced find call
            List<Future<Cursor>> result = lookupBatch.find();

            // go backwards and assign futures
            for (int i = result.size(); i > 0;) {
                assert current.isRow(id - 1);
                if (current.row(id) == EMPTY_CURSOR) {
                    // outer join support - skip row with existing empty cursor
                    current = current.prev;
                    continue;
                }
                assert current.row(id) == null;
                Future<Cursor> future = result.get(--i);
                if (future == null) {
                    current.updateRow(id, EMPTY_CURSOR, JoinRow.S_NULL, JoinRow.S_CURSOR);
                } else {
                    current.updateRow(id, future, JoinRow.S_NULL, JoinRow.S_FUTURE);
                }
                if (current.prev == null || i == 0) {
                    break;
                }
                current = current.prev;
            }

            // handle empty cursors (because of outer joins) at the beginning
            while (current.prev != null && current.prev.row(id) == EMPTY_CURSOR) {
                current = current.prev;
            }
            assert current.prev == null || current.prev.isRow(id);
            assert current.row(id) != null;
            assert !current.isRow(id);

            // the last updated row
            return current;
        }

        @Override
        public String toString() {
            return "JoinFilter->" + filter;
        }
    }

    /**
     * Linked row in batched join.
     */
    private static final class JoinRow {
        private static final long S_NULL = 0;
        private static final long S_FUTURE = 1;
        private static final long S_CURSOR = 2;
        private static final long S_ROW = 3;

        private static final long S_MASK = 3;

        /**
         * May contain one of the following:
         * <br/>- {@code null}: means that we need to get future cursor for this row
         * <br/>- {@link Future}: means that we need to get a new {@link Cursor} from the {@link Future}
         * <br/>- {@link Cursor}: means that we need to fetch {@link Row}s from the {@link Cursor}
         * <br/>- {@link Row}: the {@link Row} is already fetched and is ready to be used
         */
        private Object[] row;
        private long state;

        private JoinRow prev;
        private JoinRow next;

        /**
         * @param row Row.
         */
        private JoinRow(Object[] row) {
            this.row = row;
        }

        /**
         * @param joinFilterId Join filter id.
         * @return Row state.
         */
        private long getState(int joinFilterId) {
            return (state >>> (joinFilterId << 1)) & S_MASK;
        }

        /**
         * Allows to do a state transition in the following order:
         * 0. Slot contains {@code null} ({@link #S_NULL}).
         * 1. Slot contains {@link Future} ({@link #S_FUTURE}).
         * 2. Slot contains {@link Cursor} ({@link #S_CURSOR}).
         * 3. Slot contains {@link Row} ({@link #S_ROW}).
         *
         * @param joinFilterId {@link JoinRow} filter id.
         * @param i Increment by this number of moves.
         */
        private void incrementState(int joinFilterId, long i) {
            assert i > 0 : i;
            state += i << (joinFilterId << 1);
        }

        private void updateRow(int joinFilterId, Object x, long oldState, long newState) {
            assert getState(joinFilterId) == oldState : "old state: " + getState(joinFilterId);
            row[joinFilterId] = x;
            incrementState(joinFilterId, newState - oldState);
            assert getState(joinFilterId) == newState : "new state: " + getState(joinFilterId);
        }

        private Object row(int joinFilterId) {
            return row[joinFilterId];
        }

        private boolean isRow(int joinFilterId) {
            return getState(joinFilterId) == S_ROW;
        }

        private boolean isFuture(int joinFilterId) {
            return getState(joinFilterId) == S_FUTURE;
        }

        private boolean isCursor(int joinFilterId) {
            return getState(joinFilterId) == S_CURSOR;
        }

        private boolean isComplete() {
            return isRow(row.length - 1);
        }

        private boolean isDropped() {
            return row == null;
        }

        private void drop() {
            if (prev != null) {
                prev.next = next;
            }
            if (next != null) {
                next.prev = prev;
            }
            row = null;
        }

        /**
         * Copy this JoinRow behind itself in linked list of all in progress rows.
         *
         * @param jfId The last fetched filter id.
         * @return The copy.
         */
        private JoinRow copyBehind(int jfId) {
            assert isCursor(jfId);
            assert jfId + 1 == row.length || row[jfId + 1] == null;

            Object[] r = new Object[row.length];
            if (jfId != 0) {
                System.arraycopy(row, 0, r, 0, jfId);
            }
            JoinRow copy = new JoinRow(r);
            copy.state = state;

            if (prev != null) {
                copy.prev = prev;
                prev.next = copy;
            }
            prev = copy;
            copy.next = this;

            return copy;
        }

        @Override
        public String toString() {
            return "JoinRow->" + Arrays.toString(row);
        }
    }

    /**
     * Fake Lookup batch for indexes which do not support batching but have to participate 
     * in batched joins.
     */
    private static final class FakeLookupBatch implements IndexLookupBatch {
        private final TableFilter filter;

        private SearchRow first;
        private SearchRow last;

        private boolean full;

        private final List<Future<Cursor>> result = new SingletonList<Future<Cursor>>();

        /**
         * @param index Index.
         */
        public FakeLookupBatch(TableFilter filter) {
            this.filter = filter;
        }

        @Override
        public void reset() {
            full = false;
            first = last = null;
            result.set(0, null);
        }

        @Override
        public void addSearchRows(SearchRow first, SearchRow last) {
            assert !full;
            this.first = first;
            this.last = last;
            full = true;
        }

        @Override
        public boolean isBatchFull() {
            return full;
        }

        @Override
        public List<Future<Cursor>> find() {
            if (!full) {
                return Collections.emptyList();
            }
            Cursor c = filter.getIndex().find(filter, first, last);
            result.set(0, new DoneFuture<Cursor>(c));
            full = false;
            first = last = null;
            return result;
        }
    }

    /**
     * Simple singleton list.
     */
    private static final class SingletonList<E> extends AbstractList<E> {
        private E element;

        @Override
        public E get(int index) {
            assert index == 0;
            return element;
        }

        @Override
        public E set(int index, E element) {
            assert index == 0;
            this.element = element;
            return null;
        }

        @Override
        public int size() {
            return 1;
        }
    }
    
    /**
     * Lookup batch over this join batch for a sub-query or view.
     */
    private final class ViewIndexLookupBatch implements IndexLookupBatch {
        private final ViewIndex viewIndex;
        private final ArrayList<Future<Cursor>> result = New.arrayList();
        private boolean findCalled;

        private ViewIndexLookupBatch(ViewIndex viewIndex) {
            this.viewIndex = viewIndex;
        }

        @Override
        public void addSearchRows(SearchRow first, SearchRow last) {
            if (findCalled) {
                result.clear();
                findCalled = false;
            }
            viewIndex.setupQueryParameters(viewIndex.getSession(), first, last, null);
            if (top.collectSearchRows()) {
                result.add(PLACEHOLDER);
                if (top.isBatchFull()) {
                    // TODO
                }
            } else {
                result.add(null);
            }
        }

        @Override
        public boolean isBatchFull() {
            assert !findCalled;
            return top.isBatchFull();
        }

        @Override
        public List<Future<Cursor>> find() {
            JoinBatch jb = JoinBatch.this;
            List<Future<Cursor>> topCursors = top.find();
            for (int i = 0; i < topCursors.size(); i++) {
//                jb.setCursor
                
                while (jb.next()) {
                    // collect result
                }
            }
            findCalled = true;
            return result;
        }

        @Override
        public void reset() {
            findCalled = false;
            result.clear();
            JoinBatch.this.reset();
        }
    }
    
    /**
     * State of the  
     */
    enum State {
        
    }
}
