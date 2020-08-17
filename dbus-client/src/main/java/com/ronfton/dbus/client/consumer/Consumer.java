package com.ronfton.dbus.client.consumer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Description consumer注解
 * @author somiya
 * @date 2020/8/12 2:25 PM
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface Consumer
{
    /**
     * getRelay(id)，用于指定relay id
     * @return
     */
    String value();

    /**
     * sourceId
     * @return
     */
    Class<? extends BaseEntity> clazz();
}
