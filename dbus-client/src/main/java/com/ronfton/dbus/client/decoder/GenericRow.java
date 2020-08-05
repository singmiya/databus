package com.ronfton.dbus.client.decoder;

import org.apache.avro.generic.GenericRecord;

/**
 * @Description 订阅获取到的数据，包含修改前和修改后的数据
 * @author somiya
 * @date 2020/8/5 5:26 PM
 */
public class GenericRow
{
    GenericRecord before;
    GenericRecord after;

    public GenericRow(GenericRecord after, GenericRecord before) {
        this.after = after;
        this.before = before;
    }

    public Object getAfterClumn(String key) {
        if (null == after) {
            return null;
        }
        return this.after.get(key);
    }

    public Object getBeforeClumn(String key) {
        if (null == after) {
            return null;
        }
        return this.after.get(key);
    }

    @Override
    public String toString()
    {
        return "{after: " + (null == after ? "null" : after.toString()) + "; before: " + (null == before ? "null" : before.toString()) + "}";
    }
}
