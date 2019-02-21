/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.core.executor.sql.execute.result;

import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import lombok.SneakyThrows;
import org.apache.shardingsphere.core.executor.sql.execute.row.QueryRow;
import org.apache.shardingsphere.core.rule.ShardingRule;
import org.apache.shardingsphere.spi.algorithm.encrypt.ShardingEncryptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

/**
 * Query result meta data.
 *
 * @author panjuan
 */
public final class QueryResultMetaData {
    
    private final Multimap<String, Integer> columnLabelAndIndexes;
    
    private final ResultSetMetaData resultSetMetaData;
    
    private final ShardingRule shardingRule;
    
    @SneakyThrows 
    public QueryResultMetaData(final ResultSetMetaData resultSetMetaData, final ShardingRule shardingRule) {
        columnLabelAndIndexes = getColumnLabelAndIndexMap(resultSetMetaData);
        this.resultSetMetaData = resultSetMetaData;
        this.shardingRule = shardingRule;
    }
    
    @SneakyThrows
    private Multimap<String, Integer> getColumnLabelAndIndexMap(final ResultSetMetaData resultSetMetaData) {
        Multimap<String, Integer> result = HashMultimap.create();
        for (int columnIndex = 1; columnIndex <= resultSetMetaData.getColumnCount(); columnIndex++) {
            result.put(resultSetMetaData.getColumnLabel(columnIndex), columnIndex);
        }
        return result;
    }
    
    /**
     * Get column count.
     * 
     * @return column count
     */
    public int getColumnCount() {
        return columnLabelAndIndexes.size();
    }
    
    @Override
    public String getColumnLabel(final int columnIndex) throws SQLException {
        for (Entry<String, Integer> entry : columnLabelAndIndexes.entries()) {
            if (columnIndex == entry.getValue()) {
                return entry.getKey();
            }
        }
        throw new SQLException("Column index out of range", "9999");
    }
    
    private Integer getColumnIndex(final String columnLabel) {
        return new ArrayList<>(columnLabelAndIndexes.get(columnLabel)).get(0);
    }
    
    @SneakyThrows
    private Object decrypt(final String columnLabel, final Object value) {
        return decrypt(getColumnIndex(columnLabel), value);
    }
    
    @SneakyThrows
    private Object decrypt(final int columnIndex, final Object value) {
        Optional<ShardingEncryptor> shardingEncryptor = getShardingEncryptor(getLogicTableName(columnIndex), resultSetMetaData.getColumnName(columnIndex));
        return shardingEncryptor.isPresent() ? shardingEncryptor.get().decrypt(value) : value;
    }
    
    @SneakyThrows
    private String getLogicTableName(final int columnIndex) {
        String actualTableName = resultSetMetaData.getTableName(columnIndex);
        return shardingRule.getLogicTableNames(actualTableName).isEmpty() ? actualTableName : shardingRule.getLogicTableNames(actualTableName).iterator().next();
    }
    
    private Optional<ShardingEncryptor> getShardingEncryptor(final String logicTableName, final String columnName) {
        return shardingRule.getShardingEncryptorEngine().getShardingEncryptor(logicTableName, columnName);
    }
}
