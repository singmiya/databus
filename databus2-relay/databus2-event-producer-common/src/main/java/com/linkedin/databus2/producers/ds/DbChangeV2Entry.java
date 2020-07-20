package com.linkedin.databus2.producers.ds;

import com.linkedin.databus.core.DbusOpcode;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import java.util.List;

public class DbChangeV2Entry extends DbChangeEntry
{
    private GenericRecord beforedRecord;

    public GenericRecord getBeforedRecord()
    {
        return beforedRecord;
    }

    public DbChangeV2Entry(long scn, long timestampNanos, GenericRecord beforedRecord, GenericRecord record, DbusOpcode opCode,
                         boolean isReplicated, Schema schema, List<KeyPair> pkeys) {
        super(scn, timestampNanos, record, opCode, isReplicated, schema, pkeys);
        this.beforedRecord = beforedRecord;
    }

}
